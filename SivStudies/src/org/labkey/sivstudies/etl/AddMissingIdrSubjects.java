package org.labkey.sivstudies.etl;

import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.TableSelector;
import org.labkey.api.di.TaskRefTask;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.ContainerUser;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AddMissingIdrSubjects implements TaskRefTask
{
    protected ContainerUser _containerUser;

    @Override
    public RecordedActionSet run(@NotNull PipelineJob pipelineJob) throws PipelineJobException
    {
        // Find existing IDs:
        UserSchema us = QueryService.get().getUserSchema(_containerUser.getUser(), _containerUser.getContainer(), "study");
        if (us == null)
        {
            throw new PipelineJobException("Missing study schema");
        }

        List<String> existingIds = new ArrayList<>(new TableSelector(us.getTable("demographics"), PageFlowUtil.set("Id"), null, null).getArrayList(String.class));

        // Source IDs:
        Container sourceContainer = ContainerManager.getForPath("Labs/Bimber");
        UserSchema us2 = QueryService.get().getUserSchema(_containerUser.getUser(), sourceContainer, "bimber_data");
        if (us2 == null)
        {
            throw new PipelineJobException("Missing bimber_data schema");
        }

        List<String> allIds = new ArrayList<>(new TableSelector(us2.getTable("subjects"), PageFlowUtil.set("Rh"), null, null).getArrayList(String.class));

        allIds.removeAll(existingIds);
        if (allIds.isEmpty())
        {
            return null;
        }

        allIds = new ArrayList<>(new CaseInsensitiveHashSet(allIds));

        pipelineJob.getLogger().info("Creating {} subjects", allIds.size());
        List<Map<String, Object>> toInsert = new ArrayList<>();
        allIds.forEach(id -> {
            toInsert.add(Map.of("Id", id));
        });

        try
        {
            BatchValidationException bve = new BatchValidationException();
            us.getTable("demographics").getUpdateService().insertRows(_containerUser.getUser(), _containerUser.getContainer(), toInsert, bve, null, null);
            if (bve.hasErrors())
            {
                throw bve;
            }
        }
        catch (BatchValidationException | SQLException | DuplicateKeyException | QueryUpdateServiceException e)
        {
            throw new PipelineJobException(e);
        }

        return null;
    }

    @Override
    public List<String> getRequiredSettings()
    {
        return List.of();
    }

    @Override
    public void setSettings(Map<String, String> map) throws XmlException
    {

    }

    @Override
    public void setContainerUser(ContainerUser containerUser)
    {
        _containerUser = containerUser;
    }
}
