package org.labkey.mgap.pipeline;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 8/24/2016.
 */
public class SnpSiftWrapper extends AbstractCommandWrapper
{
    public SnpSiftWrapper(Logger log)
    {
        super(log);
    }

    public void runSnpSift(File dbnsfpFile, File input, File output) throws PipelineJobException
    {
        getLogger().info("Annotating VCF with SnpSift");

        List<String> params = new ArrayList<>();
        params.add(SequencePipelineService.get().getJavaFilepath());
        params.addAll(SequencePipelineService.get().getJavaOpts());
        params.add("-jar");
        params.add(getSnpSiftJar().getPath());
        params.add("DbNsfp");
        params.add("-db");
        params.add(dbnsfpFile.getPath());

        params.add("-noDownload");

        params.add(input.getPath());

        File unzippedVcf = new File(getOutputDir(output), "snpSift.vcf");
        execute(params, unzippedVcf);

        if (!unzippedVcf.exists())
        {
            throw new PipelineJobException("output not found: " + unzippedVcf.getName());
        }

        unzippedVcf = SequenceAnalysisService.get().bgzipFile(unzippedVcf, getLogger());
        try
        {
            if (!unzippedVcf.equals(output))
            {
                if (output.exists())
                {
                    getLogger().debug("deleting pre-existing output file: " + output.getPath());
                    output.delete();
                }
                FileUtils.moveFile(unzippedVcf, output);
            }
            SequenceAnalysisService.get().ensureVcfIndex(output, getLogger());
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private File getJarDir()
    {
        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("SNPEFFPATH");
        if (path != null)
        {
            return new File(path, "snpEff");
        }

        path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath(SequencePipelineService.SEQUENCE_TOOLS_PARAM);
        if (path == null)
        {
            path = PipelineJobService.get().getAppProperties().getToolsDirectory();
        }

        return path == null ? new File("snpEff") : new File(path, "snpEff");
    }

    public File getSnpSiftJar()
    {
        return new File(getJarDir(), "SnpSift.jar");
    }
}
