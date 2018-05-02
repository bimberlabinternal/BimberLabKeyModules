package org.labkey.mgap.pipeline;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.io.File;

public class CassandraRunner extends AbstractCommandWrapper
{
    public CassandraRunner(Logger log)
    {
        super(log);
    }

    public File execute(File inputVcfUnzipped, File outputVcfUnzipped, List<String> extraArgs) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.add(SequencePipelineService.get().getJavaFilepath());
        args.addAll(SequencePipelineService.get().getJavaOpts());
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
