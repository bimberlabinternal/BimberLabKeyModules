package org.labkey.tcrdb.pipeline;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.reader.Readers;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.MergedDirectoryResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.Path;
import org.labkey.tcrdb.TCRdbModule;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 6/16/2016.
 */
public class MiXCRWrapper extends AbstractCommandWrapper
{
    private File _libraryPath = null;

    public MiXCRWrapper(Logger log)
    {
        super(log);
    }

    public File doAlignmentAndAssemble(File fq1, @Nullable File fq2, String outputPrefix, String species, List<String> alignParams, List<String> assembleParams, boolean doAssemblePartial) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.addAll(getBaseArgs());
        //NOTE: can manually set this.  for now assume the LK user's home directory
        //args.add("-Dlibrary.path");

        args.add("align");
        args.add("-f");
        args.add("-r");
        File logFile = new File(getOutputDir(fq1), outputPrefix + ".log.txt");
        args.add(logFile.getPath());

        args.add("-s");
        args.add(species);

        args.add("-OallowPartialAlignments=true");
        args.add("-g");
        args.add("--save-description");

        if (alignParams != null)
        {
            args.addAll(alignParams);
        }

        args.add(fq1.getPath());
        if (fq2 != null)
        {
            args.add(fq2.getPath());
        }

        File alignOut = new File(getOutputDir(fq1), outputPrefix + ".align.vdjca.gz");
        args.add(alignOut.getPath());

        execute(args);

        writeLogToLog(logFile);

        File inputForFinalAlignment;
        if (doAssemblePartial)
        {
            getLogger().info("assembling partial reads, round 1");
            File partialAssembleOut1 = new File(getOutputDir(fq1), outputPrefix + ".assemblePartial_1.vdjca.gz");
            doAssemblePartial(alignOut, partialAssembleOut1, logFile);

            getLogger().info("assembling partial reads, round 2");
            File partialAssembleOut2 = new File(getOutputDir(fq1), MiXCRAnalysis.getFinalVDJFileName(outputPrefix, doAssemblePartial));
            doAssemblePartial(partialAssembleOut1, partialAssembleOut2, logFile);

            inputForFinalAlignment = partialAssembleOut2;
        }
        else
        {
            getLogger().info("assemble partial will not be used");
            inputForFinalAlignment = alignOut;
        }

        //getLogger().info("extend alignments");
        //File extendAlignmentsOut = new File(getOutputDir(fq1), MiXCRAnalysis.getFinalVDJFileName(outputPrefix));
        //doExtendAlignments(partialAssembleOut2, extendAlignmentsOut, logFile);

        getLogger().info("assembling final reads");
        File assembleOut = new File(getOutputDir(fq1), MiXCRAnalysis.getClonesFileName(outputPrefix));
        List<String> assembleArgs = new ArrayList<>();
        assembleArgs.addAll(getBaseArgs());
        assembleArgs.add("assemble");
        assembleArgs.add("-f");
        assembleArgs.add("-r");
        assembleArgs.add(logFile.getPath());

        File indexFile = new File(getOutputDir(fq1), MiXCRAnalysis.getClonesFileName(outputPrefix) + ".index");
        assembleArgs.add("--index");
        assembleArgs.add(indexFile.getPath());

        assembleArgs.add("-OaddReadsCountOnClustering=true");
        assembleArgs.add("-ObadQualityThreshold=10");

        if (assembleParams != null)
        {
            assembleArgs.addAll(assembleParams);
        }

        assembleArgs.add(inputForFinalAlignment.getPath());
        assembleArgs.add(assembleOut.getPath());

        execute(assembleArgs);
        writeLogToLog(logFile);

        return assembleOut;
    }

    //public void exportReadsForClones(File vdjca, File index, File outDir, String basename)
    //{
    //    //mixcr exportReadsForClones index_file alignments.vdjca.gz 0 reads.fastq.gz
    //}

    private void doAssemblePartial(File alignOut, File output, File logFile) throws PipelineJobException
    {
        List<String> partialAssembleArgs = new ArrayList<>();
        partialAssembleArgs.addAll(getBaseArgs());
        partialAssembleArgs.add("assemblePartial");
        partialAssembleArgs.add("-f");
        partialAssembleArgs.add("-r");
        partialAssembleArgs.add(logFile.getPath());
        partialAssembleArgs.add(alignOut.getPath());
        partialAssembleArgs.add(output.getPath());
        execute(partialAssembleArgs);

        writeLogToLog(logFile);
    }

    private void writeLogToLog(File log) throws PipelineJobException
    {
        if (!log.exists())
        {
            getLogger().warn("expected log file does not exist: " + log.getPath());
            return;
        }

        try (BufferedReader reader = Readers.getReader(log))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                getLogger().debug(line);
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        log.delete();
    }

    public void doExportClones(File clones, File output, @Nullable String locus, List<String> extraParams) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.addAll(getBaseArgs());
        args.add("exportClones");

        if (locus != null)
        {
            args.add("-c");
            args.add(locus);
        }

        if (extraParams != null)
        {
            args.addAll(extraParams);
        }

        args.add("-s");  //no spaces in header
        args.add("-cloneId");
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
        args.add("-vGene");
        args.add("-vGenes");
        args.add("-dGene");
        args.add("-dGenes");
        args.add("-jGene");
        args.add("-jGenes");
        args.add("-cGene");
        args.add("-cGenes");
        args.add("-vBestIdentityPercent");
        args.add("-dBestIdentityPercent");
        args.add("-jBestIdentityPercent");
        args.add("-nFeature");
        args.add("CDR3");
        args.add("-qFeature");
        args.add("CDR3");
        args.add("-sequence");
        args.add("-nFeature");
        args.add("VDJTranscript");
        args.add("-f");
        args.add(clones.getPath());
        args.add(output.getPath());

        execute(args);
    }

    public void doExportAlignments(File align, File output, List<String> extraArgs) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.addAll(getBaseArgs());
        args.add("exportAlignmentsPretty");
        if (extraArgs != null)
        {
            args.addAll(extraArgs);
        }
        args.add(align.getPath());
        args.add(output.getPath());

        execute(args);
    }

    private List<String> getBaseArgs() throws PipelineJobException
    {
        try
        {
            List<String> args = new ArrayList<>();
            args.add(SequencePipelineService.get().getJavaFilepath());
            //args.addAll(SequencePipelineService.get().getJavaOpts());
            File jar = new File(getJAR().getPath());
            args.add("-Dmixcr.path=" + jar.getParentFile().getPath());
            args.add("-Dmixcr.command=mixcr");
            args.add("-XX:+AggressiveOpts");

            if (_libraryPath != null)
            {
                args.add("-Dlibrary.path=" + _libraryPath.getPath());
            }

            args.add("-jar");
            args.add(jar.getPath());

            return args;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private File getJAR() throws IOException
    {
        //preferentially use copy in pipeline tools dir
        File jar = SequencePipelineService.get().getExeForPackage("mixcr", "mixcr.jar");
        if (jar.exists())
        {
            return jar;
        }

        //otherwise use checked in copy
        jar = lookupFile("external");
        return new File(jar, "mixcr-2.0.3.jar");
    }

    public String getVersionString() throws PipelineJobException
    {
        List<String> args = getBaseArgs();
        args.add("-v");

        String ret = executeWithOutput(args);
        String[] lines = ret.split("\\n");
        if (lines.length == 0)
        {
            return "Unknown";
        }

        String[] tokens = lines[0].split(" \\(");

        return tokens.length > 0 ? tokens[0] : "Unable to determine";
    }

    public void setLibraryPath(File libraryPath)
    {
        _libraryPath = libraryPath;
    }

    private File lookupFile(String path) throws IOException
    {
        Module module = ModuleLoader.getInstance().getModule(TCRdbModule.class);
        MergedDirectoryResource resource = (MergedDirectoryResource)module.getModuleResolver().lookup(Path.parse(path));
        assert resource != null : "Unable to find resource with path: " + path;

        File file = null;
        for (Resource r : resource.list())
        {
            if (r instanceof FileResource)
            {
                file = ((FileResource) r).getFile().getParentFile();
                break;
            }
        }

        if (file == null)
            throw new FileNotFoundException("Not found: " + path);

        if (!file.exists())
            throw new FileNotFoundException("Not found: " + file.getPath());

        return file;
    }
}
