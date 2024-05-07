package org.labkey.mgap.etl;

import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.di.TaskRefTask;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.writer.ContainerUser;

import java.util.List;
import java.util.Map;

public class PerformQueuedEtlWorkTask implements TaskRefTask
{
    private ContainerUser _containerUser = null;

    @Override
    public RecordedActionSet run(@NotNull PipelineJob job) throws PipelineJobException
    {
        EtlQueueManager.get().performQueuedWork(_containerUser.getContainer(), job.getLogger());
        return new RecordedActionSet();
    }

    @Override
    public List<String> getRequiredSettings()
    {
        return null;
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
