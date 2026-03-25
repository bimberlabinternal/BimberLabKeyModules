package org.labkey.mcc.etl;

import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.di.TaskRefTask;
import org.labkey.api.ehr.EHRDemographicsService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.query.QueryService;
import org.labkey.api.writer.ContainerUser;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CacheDemographicsStep implements TaskRefTask
{
    protected ContainerUser _containerUser;

    @Override
    public RecordedActionSet run(@NotNull PipelineJob job) throws PipelineJobException
    {
        TableInfo demographics = QueryService.get().getUserSchema(_containerUser.getUser(), _containerUser.getContainer(), "study").getTable("demographics");
        List<String> ids = new TableSelector(demographics, Collections.singleton("Id"), null, null).getArrayList(String.class);

        EHRDemographicsService.get().getAnimals(_containerUser.getContainer(), ids);

        return new RecordedActionSet();
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
