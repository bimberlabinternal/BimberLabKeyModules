package org.labkey.pmr.etl;

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
import org.labkey.api.di.DataIntegrationService;
import org.labkey.api.di.TaskRefTask;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SubjectScopedSelect implements TaskRefTask
{
    protected final Map<String, String> _settings = new CaseInsensitiveHashMap<>();
    protected ContainerUser _containerUser;

    private enum Settings
    {
        subjectRemoteSource(false),
        subjectSourceContainerPath(true),
        subjectSourceSchema(true),
        subjectSourceQuery(true),
        subjectSourceColumn(true),

        dataRemoteSource(false),
        dataSourceContainerPath(true),
        dataSourceSchema(true),
        dataSourceQuery(true),
        dataSourceColumns(true),
        dataSourceColumnMapping(false),
        dataSourceAdditionalFilters(false),

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

    final int BATCH_SIZE = 500;

    @Override
    public RecordedActionSet run(@NotNull PipelineJob job) throws PipelineJobException
    {
        List<String> subjects = getSubjects(job.getLogger());
        List<List<String>> batches = Lists.partition(subjects, BATCH_SIZE);
        job.getLogger().info("Total batches: " + batches.size());
        batches.forEach(x -> processBatch(x, job.getLogger()));

        return new RecordedActionSet();
    }

    private void processBatch(List<String> subjects, Logger log)
    {
        log.info("processing batch with " + subjects.size() + " subjects");
        TableInfo destinationTable = getDataDestinationTable();

        QueryUpdateService qus = destinationTable.getUpdateService();
        qus.setBulkLoad(true);

        try
        {
            // Find / Delete existing values:
            Set<ColumnInfo> keyFields = destinationTable.getColumns().stream().filter(ColumnInfo::isKeyField).collect(Collectors.toSet());
            final SimpleFilter subjectFilter = new SimpleFilter(FieldKey.fromString(_settings.get(Settings.targetSubjectColumn.name())), subjects, CompareType.IN);
            if (_settings.get(Settings.targetAdditionalFilters.name()) != null)
            {
                List<CompareType.CompareClause> additionalFilters = parseAdditionalFilters(_settings.get(Settings.targetAdditionalFilters.name()));
                additionalFilters.forEach(subjectFilter::addCondition);
            }

            Collection<Map<String, Object>> existingRows = new TableSelector(destinationTable, keyFields, subjectFilter, null).getMapCollection();
            if (!existingRows.isEmpty())
            {
                log.info("deleting " + existingRows.size() + " rows");
                qus.deleteRows(_containerUser.getUser(), _containerUser.getContainer(), new ArrayList<>(existingRows), null, null);
            }
            else
            {
                log.info("No rows to delete for this subject batch");
            }

            // Query data and import
            List<Map<String, Object>> toImport = getRowsToImport(log);
            if (!toImport.isEmpty())
            {
                log.info("inserting " + toImport.size() + " rows");
                qus.insertRows(_containerUser.getUser(), _containerUser.getContainer(), toImport, new BatchValidationException(), null, null);
            }
            else
            {
                log.info("No rows to import for this subject batch");
            }
        }
        catch (SQLException | InvalidKeyException | BatchValidationException | QueryUpdateServiceException | DuplicateKeyException e)
        {
            throw new IllegalStateException("Error Importing Rows", e);
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
        String[] filters = rawVal.split(",");
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

    private List<Map<String, Object>> getRowsToImport(Logger log)
    {
        if (_settings.get(Settings.dataSourceColumns.name()) == null)
        {
            throw new IllegalStateException("Missing value for dataSourceColumns");
        }
        List<String> sourceColumns = Arrays.asList(_settings.get(Settings.dataSourceColumns.name()).split(","));

        Map<String, String> sourceToDestColumMap = parseSourceToDestColumnMap(_settings.get(Settings.dataSourceColumnMapping.name()));

        if (_settings.get(Settings.dataRemoteSource.name()) != null)
        {
            DataIntegrationService.RemoteConnection rc = getRemoteDataSource(_settings.get(Settings.dataRemoteSource.name()), log);
            SelectRowsCommand sr = new SelectRowsCommand(_settings.get(Settings.dataSourceSchema.name()), _settings.get(Settings.dataSourceQuery.name()));
            sr.setColumns(sourceColumns);
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

                    sr.addFilter(new Filter(f.getFieldKey().toString(), value, Filter.Operator.valueOf(f.getCompareType().getFilterValueText())));
                }
            }

            try
            {
                SelectRowsResponse srr = sr.execute(rc.connection, rc.remoteContainer);

                return doNameMapping(srr.getRows(), sourceToDestColumMap);
            }
            catch (CommandException | IOException e)
            {
                throw new IllegalStateException(e);
            }
        }
        else
        {
            Container source = ContainerManager.getForPath(_settings.get(Settings.dataSourceContainerPath.name()));
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


            final SimpleFilter filter = new SimpleFilter();
            if (_settings.get(Settings.dataSourceAdditionalFilters.name()) != null)
            {
                List<CompareType.CompareClause> additionalFilters = parseAdditionalFilters(_settings.get(Settings.dataSourceAdditionalFilters.name()));
                additionalFilters.forEach(filter::addCondition);
            }

            TableSelector ts = new TableSelector(sourceTable, PageFlowUtil.set(_settings.get(Settings.subjectSourceColumn.name())), filter, null);

            return doNameMapping(new ArrayList<>(ts.getMapCollection()), sourceToDestColumMap);
        }
    }

    private List<Map<String, Object>> doNameMapping(List<Map<String, Object>> rows, Map<String, String> colMap)
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
            if (_settings.get(setting) == null)
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

    private DataIntegrationService.RemoteConnection getRemoteDataSource(String name, Logger log) throws IllegalStateException
    {
        DataIntegrationService.RemoteConnection rc = DataIntegrationService.get().getRemoteConnection(name, _containerUser.getContainer(), log);
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
            DataIntegrationService.RemoteConnection rc = getRemoteDataSource(_settings.get(Settings.subjectRemoteSource.name()), log);
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
            Container source = ContainerManager.getForPath(_settings.get(Settings.subjectSourceContainerPath.name()));
            if (source == null)
            {
                throw new IllegalStateException("Unknown container: " + _settings.get(Settings.subjectSourceContainerPath.name()));
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
