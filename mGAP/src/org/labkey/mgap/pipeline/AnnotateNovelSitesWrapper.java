package org.labkey.mgap.pipeline;

import org.apache.logging.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.run.AbstractDiscvrSeqWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AnnotateNovelSitesWrapper extends AbstractDiscvrSeqWrapper
{
    public AnnotateNovelSitesWrapper(Logger log)
    {
        super(log);
    }

    public File execute(File vcf, File referenceVcf, File fasta, String versionString, File vcfOutput, List<String> extraArgs) throws PipelineJobException
    {
        List<String> args = new ArrayList<>(getBaseArgs());
        args.add("AnnotateNovelSites");
        args.add("-R");
        args.add(fasta.getPath());

        args.add("-V");
        args.add(vcf.getPath());
        args.add("-rv");
        args.add(referenceVcf.getPath());

        args.add("-an");
        args.add("mGAPV");
        args.add("-ad");
        args.add("The first mGAP version where variants at this site appeared");
        args.add("-av");
        args.add(versionString);

        args.add("-O");
        args.add(vcfOutput.getPath());

        if (extraArgs != null)
        {
            args.addAll(extraArgs);
        }

        execute(args);

        return vcfOutput;
    }
}
