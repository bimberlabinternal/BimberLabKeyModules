package org.labkey.mgap.pipeline;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.run.DISCVRSeqRunner;

import java.io.File;
import java.util.List;

public class MultiSourceAnnotatorRunner extends DISCVRSeqRunner
{
    public MultiSourceAnnotatorRunner(Logger log)
    {
        super(log);
    }

    public File execute(File inputVcf, @Nullable File cassandraVcf, File liftoverRejects, @Nullable File funcotator, @Nullable File snpSift, File outputVcf, @Nullable List<String> options)  throws PipelineJobException
    {
        List<String> args = getBaseArgs("MultiSourceAnnotator");

        args.add("-V");
        args.add(inputVcf.getPath());

        if (cassandraVcf != null)
        {
            args.add("-c");
            args.add(cassandraVcf.getPath());
        }

        args.add("-lr");
        args.add(liftoverRejects.getPath());

        if (funcotator != null)
        {
            args.add("-f");
            args.add(funcotator.getPath());
        }

        if (snpSift != null)
        {
            args.add("-ss");
            args.add(snpSift.getPath());
        }

        args.add("-O");
        args.add(outputVcf.getPath());

        if (options != null)
        {
            args.addAll(options);
        }
        execute(args);

        if (!outputVcf.exists())
        {
            throw new PipelineJobException("Unable to find file: " + outputVcf.getPath());
        }

        return outputVcf;
    }
}
