package org.labkey.mgap.pipeline;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import java.io.File;

public class DISCVRSeqRunner extends AbstractCommandWrapper
{
    public DISCVRSeqRunner(Logger log)
    {
        super(log);
    }

    protected String getJarName()
    {
        return "DISCVRSeq.jar";
    }

    protected File getJar()
    {
        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("DISCVRSEQPATH");
        if (path != null)
        {
            return new File(path);
        }

        path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath(SequencePipelineService.SEQUENCE_TOOLS_PARAM);
        if (path == null)
        {
            path = PipelineJobService.get().getAppProperties().getToolsDirectory();
        }

        return path == null ? new File(getJarName()) : new File(path, getJarName());

    }
}
