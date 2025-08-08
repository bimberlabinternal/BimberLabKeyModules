package org.labkey.primeseq.etl;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.di.DataIntegrationService;
import org.labkey.api.di.TaskRefTask;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationError;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.query.Filter;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class VerifyRowCount implements TaskRefTask
{
    protected final Map<String, String> _settings = new CaseInsensitiveHashMap<>();
    protected ContainerUser _containerUser;

    private enum Settings
    {
        sourceRemoteSource(false),
        sourceContainerPath(false),
        sourceSchema(true),
        sourceQuery(true),
        sourceColumn(true),
        sourceAdditionalFilters(false),

        destSchema(true),
        destQuery(true),
        destColumn(true),
        destAdditionalFilters(false);

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

    @Override
    public RecordedActionSet run(@NotNull PipelineJob job) throws PipelineJobException
    {
        verifyRows(job);

        return new RecordedActionSet();
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

    private long getDestinationTableCount()
    {
        UserSchema us = QueryService.get().getUserSchema(_containerUser.getUser(), _containerUser.getContainer(), _settings.get(Settings.destSchema.name()));
        if (us == null)
        {
            throw new IllegalStateException("Unknown schema: " + _settings.get(Settings.destSchema.name()));
        }

        TableInfo ti = us.getTable(_settings.get(Settings.destQuery.name()));
        if (ti == null)
        {
            throw new IllegalStateException("Unknown table: " + _settings.get(Settings.destQuery.name()));
        }

        final SimpleFilter filter = new SimpleFilter();
        if (_settings.get(Settings.destAdditionalFilters.name()) != null)
        {
            if (_settings.get(Settings.destAdditionalFilters.name()) != null)
            {
                List<CompareType.AbstractCompareClause> additionalFilters = parseAdditionalFilters(_settings.get(Settings.destAdditionalFilters.name()));
                additionalFilters.forEach(filter::addCondition);
            }
        }

        TableSelector ts = new TableSelector(ti, PageFlowUtil.set(_settings.get(Settings.destColumn.name())), filter, null);

        return ts.getRowCount();
    }

    private long getSourceTableCount(PipelineJob job) throws PipelineJobException
    {
        if (_settings.get(Settings.sourceRemoteSource.name()) != null)
        {
            DataIntegrationService.RemoteConnection rc = getRemoteDataSource(_settings.get(Settings.sourceRemoteSource.name()), _containerUser.getContainer(), job.getLogger());
            SelectRowsCommand sr = new SelectRowsCommand(_settings.get(Settings.sourceSchema.name()), _settings.get(Settings.sourceQuery.name()));
            sr.setColumns(Collections.singletonList(_settings.get(Settings.sourceColumn.name())));

            if (_settings.get(Settings.sourceAdditionalFilters.name()) != null)
            {
                List<CompareType.AbstractCompareClause> additionalFilters = parseAdditionalFilters(_settings.get(Settings.sourceAdditionalFilters.name()));
                for (CompareType.AbstractCompareClause f : additionalFilters)
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

                return srr.getRowCount().longValue();
            }
            catch (IOException | CommandException e)
            {
                throw new PipelineJobException(e);
            }
        }
        else
        {
            UserSchema us = QueryService.get().getUserSchema(_containerUser.getUser(), _containerUser.getContainer(), _settings.get(Settings.sourceSchema.name()));
            if (us == null)
            {
                throw new IllegalStateException("Unknown schema: " + _settings.get(Settings.sourceSchema.name()));
            }

            TableInfo ti = us.getTable(_settings.get(Settings.sourceQuery.name()));
            if (ti == null)
            {
                throw new IllegalStateException("Unknown table: " + _settings.get(Settings.sourceQuery.name()));
            }

            final SimpleFilter filter = new SimpleFilter();
            if (_settings.get(Settings.sourceAdditionalFilters.name()) != null)
            {
                List<CompareType.AbstractCompareClause> additionalFilters = parseAdditionalFilters(_settings.get(Settings.sourceAdditionalFilters.name()));
                additionalFilters.forEach(filter::addCondition);
            }

            TableSelector ts = new TableSelector(ti, PageFlowUtil.set(_settings.get(Settings.sourceColumn.name())), filter, null);

            return ts.getRowCount();
        }
    }

    private List<CompareType.AbstractCompareClause> parseAdditionalFilters(String rawVal)
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
            if (fc instanceof CompareType.AbstractCompareClause cc)
            {
                return cc;
            }

            throw new IllegalStateException("Expected all filters to be instance CompareType.AbstractCompareClause, found: " + fc.getClass());
        }).toList();
    }

    private void verifyRows(PipelineJob job) throws PipelineJobException
    {
        job.getLogger().info("Verifying row count for: " + _settings.get(Settings.destSchema.name()) + "." + _settings.get(Settings.destQuery.name()));

        long source = getSourceTableCount(job);
        job.getLogger().info("Source (" + _settings.get(Settings.sourceSchema.name()) + "." + _settings.get(Settings.sourceQuery.name()) + ") row count: " + source);

        long  dest = getDestinationTableCount();
        job.getLogger().info("Destination (" + _settings.get(Settings.destSchema.name()) + "." + _settings.get(Settings.destQuery.name()) + ") row count: " + source);

        if (source != dest)
        {
            job.getLogger().error("Row counts do not match!");
        }
    }
}
