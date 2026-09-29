package org.labkey.mcc.etl;

import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.di.TaskRefTask;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.writer.ContainerUser;
import org.labkey.mcc.MccManager;

import java.util.List;
import java.util.Map;

/**
 * This step was created to prevent users from making potentially big errors and running this
 * step from the incorrect folder:
 */
public class FailUnlessMccFolderStep implements TaskRefTask
{
    protected ContainerUser _containerUser;

    @Override
    public RecordedActionSet run(@NotNull PipelineJob job) throws PipelineJobException
    {
        Container mccContainer = MccManager.get().getMCCContainer(_containerUser.getContainer());
        if (!_containerUser.getContainer().equals(mccContainer))
        {
            throw new PipelineJobException("This ETL is being executed from the wrong container, should be: " + mccContainer);
        }

        return new RecordedActionSet();
    }

    @Override
    public List<String> getRequiredSettings()
    {
        return List.of();
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
