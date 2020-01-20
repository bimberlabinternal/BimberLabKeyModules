package org.labkey.mgap.pipeline;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CassandraRunner extends AbstractCommandWrapper
{
    private Integer _maxRamOverride = null;

    public CassandraRunner(Logger log)
    {
        super(log);
    }

    public void setMaxRamOverride(Integer maxRamOverride)
    {
        _maxRamOverride = maxRamOverride;
    }

    public File execute(File inputVcfUnzipped, File outputVcfUnzipped, List<String> extraArgs) throws PipelineJobException
    {
        if (inputVcfUnzipped.getPath().endsWith(".gz") || outputVcfUnzipped.getPath().endsWith(".gz"))
        {
            throw new PipelineJobException("Expected input VCF to be unzipped:" + inputVcfUnzipped.getPath() + " / " + outputVcfUnzipped.getPath());
        }

        List<String> args = new ArrayList<>();
        args.add(SequencePipelineService.get().getJavaFilepath());
        args.addAll(SequencePipelineService.get().getJavaOpts(_maxRamOverride));
        args.add("-jar");
        args.add(getJar().getPath());
        args.add("-t");
        args.add("Annotate");

        args.add("--annovarPath");
        args.add(getAnnovarPath().getPath());

        args.add("--annovarDB");
        args.add(getAnnovarPath().getPath() + "/humandb");

        args.add("--annotationSources");
        args.add(getCassandraData().getPath());

        if (extraArgs != null && !extraArgs.isEmpty())
        {
            args.addAll(extraArgs);
        }

        args.add("-i");
        args.add(inputVcfUnzipped.getPath());

        args.add("-o");
        args.add(outputVcfUnzipped.getPath());

        args.add("--maxRecords");
        args.add("250000");

        execute(args);

        if (!outputVcfUnzipped.exists())
        {
            throw new PipelineJobException("Unable to find file: " + outputVcfUnzipped.getPath());
        }

        return outputVcfUnzipped;
    }

    private File getCassandraData()
    {
        return new File(getJar().getParentFile(), "cassandraData");
    }

    private File getAnnovarPath()
    {
        return new File(getJar().getParentFile(), "annovar");
    }

    private File getJar()
    {
        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("CASSANDRAPATH");
        if (path != null)
        {
            return new File(path);
        }

        path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath(SequencePipelineService.SEQUENCE_TOOLS_PARAM);
        if (path == null)
        {
            path = PipelineJobService.get().getAppProperties().getToolsDirectory();
        }

        return path == null ? new File("Cassandra.jar") : new File(path, "Cassandra.jar");
    }
}
