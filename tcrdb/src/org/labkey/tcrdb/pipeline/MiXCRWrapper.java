package org.labkey.tcrdb.pipeline;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 6/16/2016.
 */
public class MiXCRWrapper extends AbstractCommandWrapper
{
    public MiXCRWrapper(Logger log)
    {
        super(log);
    }

    public File doAlignmentAndAssemble(File fq1, File fq2, String outputPrefix, String species, List<String> alignParams, List<String> assembleParams) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.add(getExe().getPath());
        args.add("align");
        args.add("-f");
        args.add("-r");
        args.add(outputPrefix + ".log.txt");

        args.add("-s");
        args.add(species);

        if (alignParams != null)
        {
            args.addAll(alignParams);
        }

        args.add(fq1.getPath());
        args.add(fq2.getPath());
        File alignOut = new File(getOutputDir(fq1), outputPrefix + ".mixcr.vdjca");
        args.add(alignOut.getPath());

        execute(args);

        getLogger().info("assembling partial reads");
        File partialAssembleOut = new File(getOutputDir(fq1), outputPrefix + ".mixcr.partial.vdjca");
        List<String> partialAssembleArgs = new ArrayList<>();
        partialAssembleArgs.add(getExe().getPath());
        partialAssembleArgs.add("assemblePartial");
        partialAssembleArgs.add("-r");
        partialAssembleArgs.add(outputPrefix + ".log.txt");
        partialAssembleArgs.add(alignOut.getPath());
        partialAssembleArgs.add(partialAssembleOut.getPath());
        execute(partialAssembleArgs);

        getLogger().info("assembling final reads");
        File assembleOut = new File(getOutputDir(fq1), outputPrefix + ".mixcr.clones");
        List<String> assembleArgs = new ArrayList<>();
        assembleArgs.add(getExe().getPath());
        assembleArgs.add("assemble");
        assembleArgs.add("-f");
        assembleArgs.add("-r");
        assembleArgs.add(outputPrefix + ".log.txt");
        assembleArgs.add(partialAssembleOut.getPath());
        assembleArgs.add(assembleOut.getPath());
        if (assembleParams != null)
        {
            assembleArgs.addAll(assembleParams);
        }

        execute(assembleArgs);

        return assembleOut;
    }

     public void doExportClones(File clones, File output, String locus, List<String> extraParams) throws PipelineJobException
     {
         List<String> args = new ArrayList<>();
         args.add(getExe().getPath());
         args.add("exportClones");

         args.add("-l");
         args.add(locus);

         if (extraParams != null)
         {
             args.addAll(extraParams);
         }

         args.add("-vHit");
         args.add("-dHit");
         args.add("-jHit");
         args.add("-cHit");
         args.add("-aaFeature");
         args.add("CDR3");
         args.add("-lengthOf");
         args.add("CDR3");
         args.add("-count");
         args.add("-fraction");
         args.add("-targets");
         args.add("-vHits");
         args.add("-dHits");
         args.add("-jHits");
         args.add("-cHits");
         args.add("-vFamily");
         args.add("-vFamilies");
         args.add("-dFamily");
         args.add("-dFamilies");
         args.add("-jFamily");
         args.add("-jFamilies");
         args.add("-vBestIdentityPercent");
         args.add("-dBestIdentityPercent");
         args.add("-jBestIdentityPercent");
         args.add("-nFeature");
         args.add("CDR3");
         args.add("-qFeature");
         args.add("CDR3");
         //args.add("-sequence");
         args.add("-f");
         args.add(clones.getPath());
         args.add(output.getPath());

         execute(args);
     }

    public void doExportAlignments(File align, File output) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.add(getExe().getPath());
        args.add("exportAlignmentsPretty");
        args.add(align.getPath());
        args.add(output.getPath());

        execute(args);
    }

    private File getExe()
    {
        return SequencePipelineService.get().getExeForPackage("mixcr", "mixcr");
    }
}
