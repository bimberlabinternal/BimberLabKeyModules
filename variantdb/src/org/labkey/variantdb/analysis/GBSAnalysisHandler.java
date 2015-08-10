package org.labkey.variantdb.analysis;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.variantdb.VariantDBModule;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Created by bimber on 8/26/2014.
 */
public class GBSAnalysisHandler extends AbstractParameterizedOutputHandler
{
    private final FileType _bamType = new FileType("bam", FileType.gzSupportLevel.NO_GZ);

    public GBSAnalysisHandler()
    {
        super(ModuleLoader.getInstance().getModule(VariantDBModule.class), "GBS Analysis", "This will run a series of steps to summarize GBS data", new LinkedHashSet<>(Arrays.asList("/LDK/field/ExpDataField.js")), Arrays.asList(
                ToolParameterDescriptor.create("vcfFile", "Reference VCF", "Optional.  The DataId of a VCF file with reference variable sites.  This is used to evaluate how many covered positions overlap with these sites.", "ldk-expdatafield", null, null),
                ToolParameterDescriptor.create("maskFile", "Masked Sites", "Optional.  The DataId of a BED file with masked regions, such as output from RepeatMasker.  This is used to evaluate how many covered positions overlap with these sites.", "ldk-expdatafield", null, null),
                ToolParameterDescriptor.create("cutSitesFile", "Restriction Sites", "Optional.  The DataId of a BED file with predicted restriction enzyme cut sites.  This is used to evaluate the average distance for each GBS island from predicted sites.", "ldk-expdatafield", null, null)
        ));

    }

    @Override
    public List<String> validateParameters(JSONObject params)
    {
        return null;
    }

    @Override
    public boolean canProcess(SequenceOutputFile f)
    {
        return f.getFile() != null && (_bamType.isType(f.getFile()));
    }

    @Override
    public boolean doRunRemote()
    {
        return true;
    }

    @Override
    public boolean doRunLocal()
    {
        return false;
    }

    @Override
    public OutputProcessor getProcessor()
    {
        return new Processor();
    }

    public class Processor implements OutputProcessor
    {
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            for (ToolParameterDescriptor pd : getParameters())
            {
                if (params.containsKey(pd.getName()) && !StringUtils.isEmpty(params.getString(pd.getName())))
                {
                    ExpData d = ExperimentService.get().getExpData(params.getInt(pd.getName()));
                    if (d != null)
                    {
                        support.cacheExpData(d);
                    }
                }
            }

            for (SequenceOutputFile f : inputFiles)
            {
                if (f.getLibrary_id() != null)
                {
                    support.cacheGenome(SequenceAnalysisService.get().getReferenceGenome(f.getLibrary_id(), job.getUser()));
                }
            }
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            String scriptFile = getScriptPath(VariantDBModule.NAME, "/external/gbsAnalysis.sh");
            String histogramScript = getScriptPath("sequenceanalysis", "/external/basicHistogram.r");

            for (SequenceOutputFile o : inputFiles)
            {
                RecordedAction action = new RecordedAction("GBS Analysis");
                //action.addParameter(new RecordedAction.ParameterType("", PropertyType.STRING), "f");
                action.setStartTime(new Date());
                action.addInput(o.getFile(), "Input BAM");

                List<String> arguments = new ArrayList<>();
                arguments.add("bash");
                arguments.add(scriptFile);

                arguments.add("-h");
                arguments.add(histogramScript);

                arguments.add("-i");
                arguments.add(o.getFile().getPath());

                ReferenceGenome g = support.getCachedGenome(o.getLibrary_id());
                if (g != null)
                {
                    arguments.add("-r");
                    arguments.add(g.getWorkingFastaFile().getPath());
                }

                if (params.containsKey("vcfFile") && !StringUtils.isEmpty(params.getString("vcfFile")))
                {
                    arguments.add("-v");
                    File f = support.getCachedData(params.getInt("vcfFile"));
                    if (f == null)
                    {
                        throw new PipelineJobException("Unable to find cached file for exp data: " + params.getInt("vcfFile"));
                    }
                    arguments.add(f.getPath());
                }

                if (params.containsKey("maskFile") && !StringUtils.isEmpty(params.getString("maskFile")))
                {
                    arguments.add("-m");
                    File f = support.getCachedData(params.getInt("maskFile"));
                    if (f == null)
                    {
                        throw new PipelineJobException("Unable to find cached file for exp data: " + params.getInt("vcfFile"));
                    }
                    arguments.add(f.getPath());
                }

                if (params.containsKey("cutSitesFile") && !StringUtils.isEmpty(params.getString("cutSitesFile")))
                {
                    arguments.add("-c");
                    File f = support.getCachedData(params.getInt("cutSitesFile"));
                    if (f == null)
                    {
                        throw new PipelineJobException("Unable to find cached file for exp data: " + params.getInt("vcfFile"));
                    }
                    arguments.add(f.getPath());
                }

                String toolDir = PipelineJobService.get().getAppProperties().getToolsDirectory();
                if (!StringUtils.isEmpty(toolDir))
                {
                    arguments.add("-l");
                    arguments.add(toolDir);
                }

                AbstractCommandWrapper wrapper = new AbstractCommandWrapper(job.getLogger()){};
                wrapper.setOutputDir(outputDir);
                wrapper.setWorkingDir(outputDir);
                wrapper.execute(arguments);

                String basename = FileUtil.getBaseName(o.getFile());
                File html = new File(outputDir, basename + ".summary.html");
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(html)))
                {
                    //find outputs
                    File insertSize = new File(outputDir, basename + "_insertSize.pdf");
                    if (insertSize.exists())
                    {
                        action.addOutput(insertSize, "Insert Size Histogram", false, true);
                    }

                    writer.write("<html><body><h2>" + o.getName() + ":</h2>");
                    writer.write("This report contains multiple graphs showing summaries of GBS coverage.<p>");

                    for (String depth : Arrays.asList("3", "20", "30"))
                    {
                        writer.write("<h3>GBS Fragments With >" + depth + "X Coverage:</h3><p>");

                        File cutSites = new File(outputDir, basename + "_cutSites_" + depth + ".png");
                        if (cutSites.exists())
                        {
                            appendImage(cutSites, writer);
                        }

                        File merged = new File(outputDir, basename + "_coverage_merged_distance_" + depth + ".png");
                        if (merged.exists())
                        {
                            appendImage(merged, writer);
                        }

                        File merged2 = new File(outputDir, basename + "_coverage_merged_per_chromosome_" + depth + ".png");
                        if (merged2.exists())
                        {
                            appendImage(merged2, writer);
                        }

                        File merged3 = new File(outputDir, basename + "_fragment_length_" + depth + ".png");
                        if (merged3.exists())
                        {
                            appendImage(merged3, writer);
                        }
                    }

                    File coverageSummary = new File(outputDir, "coverage_summary.txt");
                    if (coverageSummary.exists())
                    {
                        action.addOutput(coverageSummary, "GBS Summary", false, true);
                    }

                    action.addOutput(html, "GBS Summary Report", false, true);

                    action.setEndTime(new Date());
                    actions.add(action);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }
        }

        private void appendImage(File image, BufferedWriter writer) throws IOException
        {
            String encoded = Base64.encodeBase64String(FileUtils.readFileToByteArray(image));
            writer.write("<img src=\"data:image/png;base64," + encoded + "\"/>");
            writer.write("<br>");
            image.delete();
        }

        private String getScriptPath(String moduleName, String path) throws PipelineJobException
        {
            Module module = ModuleLoader.getInstance().getModule(moduleName);
            Resource script = module.getModuleResource(path);
            if (script == null || !script.exists())
                throw new PipelineJobException("Unable to find file: " + script.getPath() + " in module: " + moduleName);

            File f = ((FileResource) script).getFile();
            if (!f.exists())
                throw new PipelineJobException("Unable to find file: " + f.getPath());

            return f.getPath();
        }
    }
}
