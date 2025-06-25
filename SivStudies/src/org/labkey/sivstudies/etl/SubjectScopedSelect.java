package org.labkey.sivstudies.etl;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.dataiterator.DetailedAuditLogDataIterator;
import org.labkey.api.di.DataIntegrationService;
import org.labkey.api.di.TaskRefTask;
import org.labkey.api.pipeline.CancelledException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationError;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.query.Filter;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.api.gwt.client.AuditBehaviorType.NONE;

public class SubjectScopedSelect implements TaskRefTask
{
    protected final Map<String, String> _settings = new CaseInsensitiveHashMap<>();
    protected ContainerUser _containerUser;

    private enum MODE
    {
        UPDATE_ONLY,
        TRUNCATE;
    }

    private enum Settings
    {
        subjectRemoteSource(false),
        subjectSourceContainerPath(false),
        subjectSourceSchema(true),
        subjectSourceQuery(true),
        subjectSourceColumn(true),

        dataRemoteSource(false),
        dataSourceContainerPath(false),
        dataSourceSchema(true),
        dataSourceQuery(true),
        dataSourceSubjectColumn(true),
        dataSourceColumns(true),
        dataSourceColumnMapping(false),
        dataSourceAdditionalFilters(false),
        dataSourceColumnDefaults(false),

        targetSchema(true),
        targetQuery(true),
        targetSubjectColumn(true),
        targetAdditionalFilters(false);

        private final boolean _isRequired;

        Settings(boolean isRequired)
        {
            _isRequired = isRequired;
        }

        public boolean isRequired()
        {
            return _isRequired;
        }
    }

    final int BATCH_SIZE = 100;

    private MODE getMode()
    {
        String rawVal = StringUtils.trimToNull(_settings.get("mode"));
        if (rawVal == null)
        {
            return MODE.TRUNCATE;
        }

        return MODE.valueOf(rawVal);
    }

    @Override
    public RecordedActionSet run(@NotNull PipelineJob job) throws PipelineJobException
    {
        List<String> subjects = getSubjects(job.getLogger());
        List<List<String>> batches = Lists.partition(subjects, BATCH_SIZE);
        job.getLogger().info("Total batches: " + batches.size());
        batches.forEach(x -> processBatch(x, job.getLogger(), job));

        return new RecordedActionSet();
    }

    private void checkCancelled(PipelineJob job)
    {
        job.updateStatusForTask();
        if (job.isCancelled())
        {
            throw new CancelledException();
        }
    }

    private void processBatch(List<String> subjects, Logger log, PipelineJob job)
    {
        log.info("processing batch with " + subjects.size() + " subjects");
        TableInfo destinationTable = getDataDestinationTable();

        QueryUpdateService qus = destinationTable.getUpdateService();
        qus.setBulkLoad(true);

        try
        {
            if (getMode() == MODE.TRUNCATE)
            {
                // Find / Delete existing values:
                Set<ColumnInfo> keyFields = destinationTable.getColumns().stream().filter(ColumnInfo::isKeyField).collect(Collectors.toSet());
                final SimpleFilter subjectFilter = new SimpleFilter(FieldKey.fromString(_settings.get(Settings.targetSubjectColumn.name())), subjects, CompareType.IN);
                if (_settings.get(Settings.targetAdditionalFilters.name()) != null)
                {
                    List<CompareType.CompareClause> additionalFilters = parseAdditionalFilters(_settings.get(Settings.targetAdditionalFilters.name()));
                    additionalFilters.forEach(subjectFilter::addCondition);
                }

                if (destinationTable.getColumn(FieldKey.fromString(_settings.get(Settings.targetSubjectColumn.name()))) == null)
                {
                    throw new IllegalStateException("Unknown column on table " + destinationTable.getName() + ": " + _settings.get(Settings.targetSubjectColumn.name()));
                }

                List<Map<String, Object>> existingRows = new ArrayList<>(new TableSelector(destinationTable, keyFields, subjectFilter, null).getMapCollection());
                if (!existingRows.isEmpty())
                {
                    List<List<Map<String, Object>>> batches = Lists.partition(existingRows, 5000);
                    log.info("deleting " + existingRows.size() + " rows in " + batches.size() + " batches");
                    int i = 0;
                    for (List<Map<String, Object>> batch : batches)
                    {
                        i++;
                        log.info("batch " + i);
                        checkCancelled(job);

                        qus.deleteRows(_containerUser.getUser(), _containerUser.getContainer(), batch, new HashMap<>(Map.of(DetailedAuditLogDataIterator.AuditConfigs.AuditBehavior, NONE, QueryUpdateService.ConfigParameters.BulkLoad, true)), null);
                    }
                }
                else
                {
                    log.info("No rows to delete for this subject batch");
                }
            }
            else
            {
                log.info("Using " + getMode().name() + " mode, source records will not be deleted");
            }

            // Query data and import
            List<Map<String, Object>> toImportOrUpdate = getRowsToImport(subjects, log);
            if (!toImportOrUpdate.isEmpty())
            {
                if (getMode() == MODE.TRUNCATE)
                {
                    List<List<Map<String, Object>>> batches = Lists.partition(toImportOrUpdate, 5000);
                    log.info("inserting " + toImportOrUpdate.size() + " rows in " + batches.size() + " batches");

                    int i = 0;
                    for (List<Map<String, Object>> batch : batches)
                    {
                        i++;
                        log.info("batch " + i);
                        checkCancelled(job);

                        BatchValidationException bve = new BatchValidationException();
                        qus.insertRows(_containerUser.getUser(), _containerUser.getContainer(), batch, bve, new HashMap<>(Map.of(DetailedAuditLogDataIterator.AuditConfigs.AuditBehavior, NONE, QueryUpdateService.ConfigParameters.BulkLoad, true)), null);
                        if (bve.hasErrors())
                        {
                            throw bve;
                        }
                    }
                }
                else if (getMode() == MODE.UPDATE_ONLY)
                {
                    List<List<Map<String, Object>>> batches = Lists.partition(toImportOrUpdate, 5000);
                    log.info("updating " + toImportOrUpdate.size() + " rows in " + batches.size() + " batches");

                    int i = 0;
                    for (List<Map<String, Object>> batch : batches)
                    {

                        i++;
                        log.info("batch " + i);
                        checkCancelled(job);

                        BatchValidationException bve = new BatchValidationException();

                        Collection<String> keyFields = destinationTable.getPkColumnNames();
                        List<Map<String, Object>> keys = batch.stream().map(x -> {
                            Map<String, Object> map = new HashMap<>();
                            for (String keyField : keyFields)
                            {
                                if (x.get(keyField) != null)
                                {
                                    map.put(keyField, x.get(keyField));
                                }
                            }

                            return map;
                        }).toList();

                        qus.updateRows(_containerUser.getUser(), _containerUser.getContainer(), batch, keys, bve, new HashMap<>(Map.of(DetailedAuditLogDataIterator.AuditConfigs.AuditBehavior, NONE, QueryUpdateService.ConfigParameters.BulkLoad, true)), null);
                        if (bve.hasErrors())
                        {
                            throw bve;
                        }
                    }
                }
                else
                {
                    throw new IllegalStateException("Unknown mode: " + getMode());
                }
            }
            else
            {
                log.info("No rows to import/update for this subject batch");
            }
        }
        catch (SQLException | InvalidKeyException | BatchValidationException | QueryUpdateServiceException | DuplicateKeyException e)
        {
            throw new IllegalStateException("Error Importing/Updating Rows", e);
        }
    }

    private List<CompareType.CompareClause> parseAdditionalFilters(String rawVal)
    {
        rawVal = StringUtils.trimToNull(rawVal);
        if (rawVal == null)
        {
            return Collections.emptyList();
        }

        SimpleFilter filter = new SimpleFilter();
        String[] filters = rawVal.split(";");
        for (String queryParam : filters)
        {
            filter.addUrlFilters(new ActionURL().setRawQuery(queryParam), null);
        }

        return filter.getClauses().stream().map(fc -> {
            if (fc instanceof CompareType.CompareClause cc)
            {
                return cc;
            }

            throw new IllegalStateException("Expected all filters to be instance CompareType.CompareClause, found: " + fc.getClass());
        }).toList();
    }

    private Map<String, String> parseSourceToDestColumnMap(String rawVal)
    {
        rawVal = StringUtils.trimToNull(rawVal);
        if (rawVal == null)
        {
            return Collections.emptyMap();
        }

        Map<String, String> colMap = new HashMap<>();

        String[] tokens = rawVal.split(",");
        for (String token : tokens)
        {
            if (!token.contains("="))
            {
                throw new IllegalStateException("Invalid columnMapping: " + token);
            }

            String[] els = token.split("=");
            if (els.length != 2)
            {
                throw new IllegalStateException("Invalid columnMapping: " + token);
            }

            colMap.put(els[0], els[1]);
        }

        return colMap;
    }

    private Map<String, String> parseColumnDefaultMap(String rawVal)
    {
        rawVal = StringUtils.trimToNull(rawVal);
        if (rawVal == null)
        {
            return Collections.emptyMap();
        }

        Map<String, String> colMap = new HashMap<>();

        String[] tokens = rawVal.split(";");
        for (String token : tokens)
        {
            if (!token.contains("="))
            {
                throw new IllegalStateException("Invalid column defaultValue: " + token);
            }

            String[] els = token.split("=");
            if (els.length != 2)
            {
                throw new IllegalStateException("Invalid column defaultValue: " + token);
            }

            colMap.put(els[0], els[1]);
        }

        return colMap;
    }

    private List<Map<String, Object>> getRowsToImport(List<String> subjects, Logger log)
    {
        if (_settings.get(Settings.dataSourceColumns.name()) == null)
        {
            throw new IllegalStateException("Missing value for dataSourceColumns");
        }
        List<String> sourceColumns = Arrays.asList(_settings.get(Settings.dataSourceColumns.name()).split(","));

        Map<String, String> sourceToDestColumMap = parseSourceToDestColumnMap(_settings.get(Settings.dataSourceColumnMapping.name()));
        Map<String, String> columnToDefaultMap = parseColumnDefaultMap(_settings.get(Settings.dataSourceColumnDefaults.name()));

        if (_settings.get(Settings.dataRemoteSource.name()) != null)
        {
            Container target;
            if (_settings.get(Settings.dataSourceContainerPath.name()) != null)
            {
                target = ContainerManager.getForPath(_settings.get(Settings.dataSourceContainerPath.name()));
                if (target == null)
                {
                    throw new IllegalStateException("Unknown container: " + _settings.get(Settings.dataSourceContainerPath.name()));
                }
            }
            else
            {
                target =_containerUser.getContainer();
            }

            DataIntegrationService.RemoteConnection rc = getRemoteDataSource(_settings.get(Settings.dataRemoteSource.name()), target, log);
            SelectRowsCommand sr = new SelectRowsCommand(_settings.get(Settings.dataSourceSchema.name()), _settings.get(Settings.dataSourceQuery.name()));
            sr.setColumns(sourceColumns);
            sr.addFilter(_settings.get(Settings.dataSourceSubjectColumn.name()), StringUtils.join(subjects, ";"), Filter.Operator.IN);
            if (_settings.get(Settings.dataSourceAdditionalFilters.name()) != null)
            {
                List<CompareType.CompareClause> additionalFilters = parseAdditionalFilters(_settings.get(Settings.dataSourceAdditionalFilters.name()));
                for (CompareType.CompareClause f : additionalFilters)
                {
                    Object value;
                    if (f.getParamVals() == null)
                    {
                        value = null;
                    }
                    else if (f.getParamVals().length == 1)
                    {
                        value = f.getParamVals()[0];
                    }
                    else
                    {
                        value = StringUtils.join(f.getParamVals(), ";");
                    }

                    Filter.Operator o = Filter.Operator.getOperatorFromUrlKey(f.getCompareType().getPreferredUrlKey());
                    if (o == null)
                    {
                        throw new IllegalStateException("Unknown operator: " + f.getCompareType().getPreferredUrlKey() + ", raw filter: " + f.getCompareType().name());
                    }

                    sr.addFilter(new Filter(f.getFieldKey().toString(), value, o));
                }
            }

            try
            {
                SelectRowsResponse srr = sr.execute(rc.connection, rc.remoteContainer);

                return doNameMapping(srr.getRows(), sourceToDestColumMap, columnToDefaultMap);
            }
            catch (CommandException | IOException e)
            {
                throw new IllegalStateException(e);
            }
        }
        else
        {
            Container source;
            if (_settings.get(Settings.dataSourceContainerPath.name()) == null)
            {
                source = _containerUser.getContainer();
            }
            else
            {
                source = ContainerManager.getForPath(_settings.get(Settings.dataSourceContainerPath.name()));
            }

            if (source == null)
            {
                throw new IllegalStateException("Unknown container: " + _settings.get(Settings.dataSourceContainerPath.name()));
            }

            if (!source.hasPermission(_containerUser.getUser(), ReadPermission.class))
            {
                throw new IllegalStateException("User does not have read permission: " + _settings.get(Settings.dataSourceContainerPath.name()));
            }

            UserSchema us = QueryService.get().getUserSchema(_containerUser.getUser(), source, _settings.get(Settings.dataSourceSchema.name()));
            if (us == null)
            {
                throw new IllegalStateException("Unknown schema: " + _settings.get(Settings.dataSourceSchema.name()));
            }

            TableInfo sourceTable = us.getTable(_settings.get(Settings.dataSourceQuery.name()));
            if (sourceTable == null)
            {
                throw new IllegalStateException("Unknown table: " + _settings.get(Settings.dataSourceQuery.name()));
            }

            if (!sourceTable.hasPermission(_containerUser.getUser(), ReadPermission.class))
            {
                throw new IllegalStateException("User does not have read permission on the source table " + _settings.get(Settings.dataSourceContainerPath.name()));
            }

            for (String colName : sourceColumns)
            {
                if (sourceTable.getColumn(colName) == null)
                {
                    throw new IllegalStateException("Table is missing column: " + colName);
                }
            }

            if (sourceTable.getColumn(_settings.get(Settings.dataSourceSubjectColumn.name())) == null)
            {
                throw new IllegalStateException("Table is missing column: " + _settings.get(Settings.dataSourceSubjectColumn.name()));
            }

            final SimpleFilter filter = new SimpleFilter(FieldKey.fromString(_settings.get(Settings.dataSourceSubjectColumn.name())), subjects, CompareType.IN);
            if (_settings.get(Settings.dataSourceAdditionalFilters.name()) != null)
            {
                List<CompareType.CompareClause> additionalFilters = parseAdditionalFilters(_settings.get(Settings.dataSourceAdditionalFilters.name()));
                additionalFilters.forEach(filter::addCondition);
            }

            TableSelector ts = new TableSelector(sourceTable, new HashSet<>(sourceColumns), filter, null);

            return doNameMapping(new ArrayList<>(ts.getMapCollection()), sourceToDestColumMap, columnToDefaultMap);
        }
    }

    private List<Map<String, Object>> doNameMapping(List<Map<String, Object>> rows, Map<String, String> colMap, Map<String, String> columnToDefaultMap)
    {
        return rows.stream().map(row -> {
            if (colMap.isEmpty())
            {
                return row;
            }

            colMap.forEach((sourceCol, destCol) -> {
                if (row.containsKey(sourceCol))
                {
                    row.put(destCol, row.get(sourceCol));
                }

                row.remove(sourceCol);
            });

            return row;
        }).map(row -> {
            for (String colName : columnToDefaultMap.keySet())
            {
                if (!row.containsKey(colName))
                {
                    row.put(colName, columnToDefaultMap.get(colName));
                }
                else if (row.get(colName) == null)
                {
                    row.put(colName, columnToDefaultMap.get(colName));
                }
            }

            return row;
        }).toList();
    }

    private TableInfo getDataDestinationTable()
    {
        UserSchema us = QueryService.get().getUserSchema(_containerUser.getUser(), _containerUser.getContainer(), _settings.get(Settings.targetSchema.name()));
        if (us == null)
        {
            throw new IllegalStateException("Unknown schema: " + _settings.get(Settings.targetSchema.name()));
        }

        TableInfo sourceTable = us.getTable(_settings.get(Settings.targetQuery.name()));
        if (sourceTable == null)
        {
            throw new IllegalStateException("Unknown table: " + _settings.get(Settings.targetQuery.name()));
        }

        return sourceTable;
    }

    @Override
    public void setContainerUser(ContainerUser containerUser)
    {
        _containerUser = containerUser;
    }

    @Override
    public List<ValidationError> preFlightCheck(Container c)
    {
        List<ValidationError> errors = new ArrayList<>();
        for (String setting : getRequiredSettings())
        {
            if (_settings.get(setting) == null || StringUtils.isEmpty(_settings.get(setting)))
            {
                errors.add(new SimpleValidationError("Missing required setting: " + setting));
            }
        }

        return errors;
    }

    @Override
    public List<String> getRequiredSettings()
    {
        return Arrays.stream(Settings.values()).filter(Settings::isRequired).map(Settings::name).toList();
    }

    @Override
    public void setSettings(Map<String, String> settings)
    {
        _settings.putAll(settings);
    }

    private DataIntegrationService.RemoteConnection getRemoteDataSource(String name, Container c, Logger log) throws IllegalStateException
    {
        DataIntegrationService.RemoteConnection rc = DataIntegrationService.get().getRemoteConnection(name, c, log);
        if (rc == null)
        {
            throw new IllegalStateException("Unable to find remote connection: " + name);
        }

        return rc;
    }

    private List<String> getSubjects(Logger log)
    {
        if (_settings.get(Settings.subjectRemoteSource.name()) != null)
        {
            Container target;
            if (_settings.get(Settings.subjectSourceContainerPath.name()) != null)
            {
                target = ContainerManager.getForPath(_settings.get(Settings.subjectSourceContainerPath.name()));
                if (target == null)
                {
                    throw new IllegalStateException("Unknown container: " + _settings.get(Settings.subjectSourceContainerPath.name()));
                }
            }
            else
            {
                target =_containerUser.getContainer();
            }

            DataIntegrationService.RemoteConnection rc = getRemoteDataSource(_settings.get(Settings.subjectRemoteSource.name()), target, log);
            SelectRowsCommand sr = new SelectRowsCommand(_settings.get(Settings.subjectSourceSchema.name()), _settings.get(Settings.subjectSourceQuery.name()));
            sr.setColumns(Arrays.asList(_settings.get(Settings.subjectSourceColumn.name())));

            try
            {
                SelectRowsResponse srr = sr.execute(rc.connection, rc.remoteContainer);

                return srr.getRows().stream().map(x -> x.get(_settings.get(Settings.subjectSourceColumn.name()))).map(Object::toString).toList();
            }
            catch (CommandException | IOException e)
            {
                throw new IllegalStateException(e);
            }
        }
        else
        {
            Container source = _settings.get(Settings.subjectSourceContainerPath.name()) == null ? _containerUser.getContainer() : ContainerManager.getForPath(_settings.get(Settings.subjectSourceContainerPath.name()));
            if (source == null)
            {
                throw new IllegalStateException("Unknown subjectSourceContainerPath: " + _settings.get(Settings.subjectSourceContainerPath.name()));
            }

            if (!source.hasPermission(_containerUser.getUser(), ReadPermission.class))
            {
                throw new IllegalStateException("User does not have read permission: " + _settings.get(Settings.subjectSourceContainerPath.name()));
            }

            UserSchema us = QueryService.get().getUserSchema(_containerUser.getUser(), source, _settings.get(Settings.subjectSourceSchema.name()));
            if (us == null)
            {
                throw new IllegalStateException("Unknown schema: " + _settings.get(Settings.subjectSourceSchema.name()));
            }

            TableInfo sourceTable = us.getTable(_settings.get(Settings.subjectSourceQuery.name()));
            if (sourceTable == null)
            {
                throw new IllegalStateException("Unknown table: " + _settings.get(Settings.subjectSourceQuery.name()));
            }

            if (sourceTable.getColumn(_settings.get(Settings.subjectSourceColumn.name())) == null)
            {
                throw new IllegalStateException("Table is missing column: " + _settings.get(Settings.subjectSourceColumn.name()));
            }

            return new TableSelector(sourceTable, PageFlowUtil.set(_settings.get(Settings.subjectSourceColumn.name()))).getArrayList(String.class);
        }
    }


}
