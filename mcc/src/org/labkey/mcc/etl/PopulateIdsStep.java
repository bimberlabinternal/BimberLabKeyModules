package org.labkey.mcc.etl;

import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.di.TaskRefTask;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.ContainerUser;
import org.labkey.mcc.MccSchema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PopulateIdsStep implements TaskRefTask
{
    protected ContainerUser _containerUser;

    @Override
    public RecordedActionSet run(@NotNull PipelineJob job) throws PipelineJobException
    {
        populateForDemographics(job);
        populateForKinship(job);

        return new RecordedActionSet();
    }

    private void populateForDemographics(PipelineJob job) throws PipelineJobException
    {
        // Query aggregated demographics:
        UserSchema sourceSchema = QueryService.get().getUserSchema(_containerUser.getUser(), _containerUser.getContainer(), MccSchema.NAME);
        if (sourceSchema == null)
        {
            throw new PipelineJobException("Unable to find source schema: " + MccSchema.NAME);
        }

        TableInfo sourceTi = sourceSchema.getTable("aggregatedDemographics");
        if (sourceTi == null)
        {
            throw new PipelineJobException("Unable to find table: aggregatedDemographics");
        }

        populateForField(job, sourceTi, "Id", "originalId");
        populateForField(job, sourceTi, "dam", "originalDam");
        populateForField(job, sourceTi, "sire", "originalSire");
    }

    private void populateForKinship(PipelineJob job) throws PipelineJobException
    {
        // Query aggregated demographics:
        UserSchema sourceSchema = QueryService.get().getUserSchema(_containerUser.getUser(), _containerUser.getContainer(), "mcc");
        if (sourceSchema == null)
        {
            throw new PipelineJobException("Unable to find source schema: " + MccSchema.NAME);
        }

        TableInfo sourceTi = sourceSchema.getTable("aggregatedKinship");
        if (sourceTi == null)
        {
            throw new PipelineJobException("Unable to find table: aggregatedKinship");
        }

        populateForField(job, sourceTi, "Id", "originalId");
        populateForField(job, sourceTi, "Id2", "originalId2");
    }

    private void populateForField(PipelineJob job, TableInfo sourceTi, String fieldName, String originalIdField)
    {
        final Map<Container, List<Map<String, Object>>> toAdd = new HashMap<>();

        SimpleFilter filter = new SimpleFilter(FieldKey.fromString(fieldName), null, CompareType.ISBLANK);
        filter.addCondition(FieldKey.fromString(originalIdField), null, CompareType.NONBLANK);

        TableSelector ts = new TableSelector(sourceTi, PageFlowUtil.set(originalIdField, "container"), filter, null);
        if (ts.exists())
        {
            ts.forEachResults(rs -> {
                Container c = ContainerManager.getForId(rs.getString(FieldKey.fromString("container")));
                List<Map<String, Object>> rows = toAdd.containsKey(c) ? toAdd.get(c) : new ArrayList<>();
                CaseInsensitiveHashMap<Object> row = new CaseInsensitiveHashMap<>();
                row.put("subjectname", rs.getString(FieldKey.fromString(originalIdField)));
                row.put("externalAlias", null); //NOTE: the trigger script will auto-assign a value, but we need to include this property on the input JSON

                rows.add(row);
                toAdd.put(c, rows);
            });
        }

        try
        {
            for (Container c : toAdd.keySet())
            {
                job.getLogger().info("Total IDs to alias for field " + fieldName + " in container: " + c.getPath() + ": " + toAdd.get(c).size());
                TableInfo ti = QueryService.get().getUserSchema(_containerUser.getUser(), c, MccSchema.NAME).getTable(MccSchema.TABLE_ANIMAL_MAPPING);
                BatchValidationException bve = new BatchValidationException();
                ti.getUpdateService().insertRows(_containerUser.getUser(), c, toAdd.get(c), bve, null, null);
                if (bve.hasErrors())
                {
                    throw bve;
                }
            }
        }
        catch (BatchValidationException | DuplicateKeyException | QueryUpdateServiceException | SQLException e)
        {
            job.getLogger().error("Error populating IDs", e);
        }
    }

    @Override
    public List<String> getRequiredSettings()
    {
        return Collections.emptyList();
    }

    @Override
    public void setSettings(Map<String, String> settings) throws XmlException
    {

    }

    @Override
    public void setContainerUser(ContainerUser containerUser)
    {
        _containerUser = containerUser;
    }
}
