package org.labkey.mgap.pipeline;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.run.PicardWrapper;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class LiftoverVcfRunner extends PicardWrapper
{
    public LiftoverVcfRunner(Logger log)
    {
        super(log);
    }

    @Override
    protected String getToolName()
    {
        return "LiftoverVcf";
    }

    public void doLiftover(File inputVcf, File chainFile, File referenceFasta, @Nullable File rejectVcf, File outputVcf, double minPctMatch) throws PipelineJobException
    {
        getLogger().info("Liftover VCF: " + inputVcf.getPath());

        List<String> params = getBaseArgs();
        params.add("--INPUT");
        params.add(inputVcf.getPath());

        params.add("--OUTPUT");
        params.add(outputVcf.getPath());

        params.add("--CHAIN");
        params.add(chainFile.getPath());

        params.add("--REFERENCE_SEQUENCE");
        params.add(referenceFasta.getPath());

        params.add("--WRITE_ORIGINAL_POSITION");
        params.add("true");

        params.add("--WRITE_ORIGINAL_ALLELES");
        params.add("true");

        params.add("--LOG_FAILED_INTERVALS");
        params.add("false");

        params.add("--LIFTOVER_MIN_MATCH");
        params.add(String.valueOf(minPctMatch));

        if (rejectVcf != null)
        {
            params.add("--REJECT");
            params.add(rejectVcf.getPath());
        }

        execute(params);

        if (!outputVcf.exists())
        {
            throw new PipelineJobException("Output file could not be found: " + outputVcf.getPath());
        }

        if (rejectVcf != null && rejectVcf.exists())
        {
            try
            {
                SequenceAnalysisService.get().ensureVcfIndex(rejectVcf, getLogger());
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }
}
