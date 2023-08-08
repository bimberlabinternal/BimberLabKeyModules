package org.labkey.mgap.pipeline;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.run.AbstractDiscvrSeqWrapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 8/24/2016.
 */
public class FuncotatorWrapper extends AbstractDiscvrSeqWrapper
{
    public FuncotatorWrapper(Logger log)
    {
        super(log);
    }

    public void runFuncotator(File dataDir, File input, File output, ReferenceGenome genome, @Nullable List<String> extraArgs) throws PipelineJobException
    {
        getLogger().info("Annotating VCF with Funcotator");

        List<String> params = new ArrayList<>(getBaseArgs("ExtendedFuncotator"));

        params.add("-R");
        params.add(genome.getWorkingFastaFile().getPath());

        params.add("--ref-version");
        params.add("hg19");

        params.add("--data-sources-path");
        params.add(dataDir.getPath());

        params.add("--output-file-format");
        params.add("VCF");

        params.add("-cf");
        params.add(new File(dataDir, "fieldConfig.txt").getPath());

        params.add("-V");
        params.add(input.getPath());

        params.add("-O");
        params.add(output.getPath());

        if (extraArgs != null)
        {
            params.addAll(extraArgs);
        }

        execute(params);

        if (!output.exists())
        {
            throw new PipelineJobException("output not found: " + output.getName());
        }

        try
        {
            SequenceAnalysisService.get().ensureVcfIndex(output, getLogger());
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }
}
