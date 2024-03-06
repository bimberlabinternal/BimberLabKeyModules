package org.labkey.mcc.etl;

import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
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
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.ContainerUser;
import org.labkey.mcc.MccManager;
import org.labkey.mcc.MccSchema;
import org.labkey.remoteapi.CommandException;
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

public class PopulateGeneticDataStep implements TaskRefTask
{
    protected final Map<String, String> _settings = new CaseInsensitiveHashMap<>();

    protected ContainerUser _containerUser;

    private enum Settings
    {
        remoteSource()
    }

    @Override
    public RecordedActionSet run(@NotNull PipelineJob job) throws PipelineJobException
    {
        populateGeneticData(job);

        return new RecordedActionSet();
    }

    private void populateGeneticData(PipelineJob job) throws PipelineJobException
    {
        DataIntegrationService.RemoteConnection rc = DataIntegrationService.get().getRemoteConnection(_settings.get(Settings.remoteSource.name()), _containerUser.getContainer(), job.getLogger());
        if (rc == null)
        {
            throw new PipelineJobException("Unable to find remote connection: " + _settings.get(Settings.remoteSource.name()));
        }

        try
        {
            //first select all rows from remote table
            SelectRowsCommand sr = new SelectRowsCommand(MccSchema.NAME, "genomicDatasetsSource");
            sr.setColumns(Arrays.asList("Id", "date", "datatype", "sra_accession"));

            TableInfo aggregatedDemographics = QueryService.get().getUserSchema(job.getUser(), job.getContainer(), MccSchema.NAME).getTable("aggregatedDemographics");

            //and select all rows from our source table
            SelectRowsResponse srr = sr.execute(rc.connection, rc.remoteContainer);
            Map<Container, List<Map<String, Object>>> toInsert = new HashMap<>();
            srr.getRows().forEach(x -> {
                String mccId = (String)x.get("Id");

                Collection<Map<String, Object>> rows = new TableSelector(aggregatedDemographics, PageFlowUtil.set("originalId", "container"), new SimpleFilter(FieldKey.fromString("Id"), mccId), null).getMapCollection();
                if (rows.isEmpty())
                {
                    job.getLogger().error("Unable to find ID: " + mccId);
                    return;
                }

                if (rows.size() > 1)
                {
                    job.getLogger().error("MCC ID linked to multiple containers: " + mccId);
                    return;
                }

                Map<String, Object> row = rows.iterator().next();

                Container target = ContainerManager.getForId(String.valueOf(row.get("container")));
                if (!toInsert.containsKey(target))
                {
                    toInsert.put(target, new ArrayList<>());
                }

                Map<String, Object> newRow = new CaseInsensitiveHashMap<>();
                newRow.put("Id", row.get("originalId"));
                newRow.put("date", x.get("date"));
                newRow.put("datatype", x.get("datatype"));
                newRow.put("sra_accession", x.get("sra_accession"));

                toInsert.get(target).add(newRow);
            });

            Container c = MccManager.get().getMCCInternalDataContainer(job.getContainer());
            if (c == null)
            {
                throw new IllegalStateException("MCCInternalDataContainer not set");
            }

            for (Container child : c.getChildren())
            {
                TableInfo ti = QueryService.get().getUserSchema(job.getUser(), child, MccSchema.NAME).getTable(MccSchema.TABLE_CENSUS);
                try
                {
                    ti.getUpdateService().truncateRows(job.getUser(), child, null, null);

                    if (toInsert.containsKey(child))
                    {
                        BatchValidationException bve = new BatchValidationException();
                        ti.getUpdateService().insertRows(job.getUser(), child, toInsert.get(child), bve, null, null);
                        if (bve.hasErrors())
                        {
                            throw bve;
                        }
                    }
                }
                catch (BatchValidationException | SQLException | DuplicateKeyException | QueryUpdateServiceException e)
                {
                    throw new PipelineJobException(e);
                }
            }
        }
        catch (CommandException | IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    @Override
    public List<String> getRequiredSettings()
    {
        return Collections.unmodifiableList(Arrays.asList(Settings.remoteSource.name()));
    }

    @Override
    public void setSettings(Map<String, String> settings) throws XmlException
    {
        _settings.putAll(settings);
    }

    @Override
    public void setContainerUser(ContainerUser containerUser)
    {
        _containerUser = containerUser;
    }
}