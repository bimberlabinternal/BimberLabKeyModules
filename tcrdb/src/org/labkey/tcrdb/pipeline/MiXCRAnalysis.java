package org.labkey.tcrdb.pipeline;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.reader.Readers;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.MergedDirectoryResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAnalysisStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.DefaultPipelineStepOutput;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.PicardWrapper;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.writer.PrintWriters;
import org.labkey.tcrdb.TCRdbModule;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Created by bimber on 5/10/2016.
 */
public class MiXCRAnalysis extends AbstractPipelineStep implements AnalysisStep
{
    public MiXCRAnalysis(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    private static String TCR_DBs = "tcrDB";
    private static String MIN_CLONE_FRACTION = "minCloneFraction";
    private static String MIN_CLONE_READS = "minCloneReads";
    private static String EXPORT_ALIGNMENTS = "exportAlignments";
    private static String IS_RNA_SEQ = "isRnaSeq";
    private static String TARGET_ASSAY = "targetAssay";
    private static String LOCI = "loci";

    public static class Provider extends AbstractAnalysisStepProvider<MiXCRAnalysis>
    {
        public Provider()
        {
            super("MiXCR", "MiXCR", null, "Any reads in the BAM file that are mapped will be fed to MiXCR for TCR sequence analysis.  Results will be imported into the selected assay.  The analysis expects the reads are aligned to some type of TCR sequence DB, which serves as a filter step to enrich for TCR-specific reads.", Arrays.asList(
                    ToolParameterDescriptor.create(TCR_DBs, "TCR DB(s)", "The sequence DB(s), usually species, to be used for alignment.", "tcr-libraryfield", new JSONObject()
                    {{
                        put("allowBlank", false);
                    }}, 17),
                    ToolParameterDescriptor.create(MIN_CLONE_FRACTION, "Min Clone Fraction", "Any CDR3 sequences will be reported if the they represent at least this fraction of total reads for that sample.", "ldk-numberfield", new JSONObject()
                    {{
                        put("minValue", 0);
                        put("maxValue", 1);
                    }}, 0.2),
                    ToolParameterDescriptor.create(MIN_CLONE_READS, "Min Reads Per Clone", "Any CDR3 sequences will be reported if the they represent at least this many reads.", "ldk-integerfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, 4),
                    ToolParameterDescriptor.create(EXPORT_ALIGNMENTS, "Export Alignments", "If checked, MiXCR will also output a text file with the actual alignments produced.  This can be helpful to debug or double check results", "checkbox", new JSONObject()
                    {{

                    }}, false),
                    ToolParameterDescriptor.create(IS_RNA_SEQ, "RNA-Seq Data", "If checked, MiXCR settings tailored to RNA-Seq (see -p rna-seq) will be used.", "checkbox", new JSONObject()
                    {{
                        put("checked", true);
                    }}, true),
                    ToolParameterDescriptor.create(TARGET_ASSAY, "Target Assay", "Results will be loaded into this assay.  If no assay is selected, a table will be created with nothing in the DB.", "tcr-assayselectorfield", new JSONObject()
                    {{
                        put("checked", true);
                    }}, true),
                    ToolParameterDescriptor.create(TARGET_ASSAY, "Loci", "Clones matching the selected loci will be exported.", "tcrdb-locusfield", new JSONObject()
                    {{
                        put("value", "TRA;TRB");
                    }}, true)
            ), Arrays.asList("tcrdb/field/LibraryField.js", "tcrdb/field/AssaySelectorField.js", "tcrdb/field/LocusField.js"), null);
        }

        @Override
        public MiXCRAnalysis create(PipelineContext ctx)
        {
            return new MiXCRAnalysis(this, ctx);
        }
    }

    @Override
    public void init(List<AnalysisModel> models) throws PipelineJobException
    {

    }

    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        MiXCROutput output = new MiXCROutput();
        output.addInput(inputBam, "Input BAM");

        getPipelineCtx().getLogger().info("creating FASTQs from BAM: " + inputBam.getName());
        SimpleScriptWrapper wrapper = new SimpleScriptWrapper(getPipelineCtx().getLogger());

        File bamScript = getScript("external/exportMappedReads.sh");
        File forwardFq = new File(outputDir, FileUtil.getBaseName(inputBam) + "-R1.fastq.gz");
        output.addIntermediateFile(forwardFq, "FASTQ Data");
        File reverseFq = new File(outputDir, FileUtil.getBaseName(inputBam) + "-R2.fastq.gz");
        output.addIntermediateFile(reverseFq, "FASTQ Data");

        wrapper.addToEnvironment("JAVA", SequencePipelineService.get().getJavaFilepath());
        wrapper.addToEnvironment("SAMTOOLS", SequencePipelineService.get().getExeForPackage("SAMTOOLSPATH", "samtools").getPath());
        wrapper.addToEnvironment("PICARD", PicardWrapper.getPicardJar().getPath());
        wrapper.setWorkingDir(outputDir);
        wrapper.execute(Arrays.asList("bash", bamScript.getPath(), inputBam.getPath(), forwardFq.getPath(), reverseFq.getPath()));

        //abort if no reads present
        if (!hasLines(forwardFq))
        {
            getPipelineCtx().getLogger().info("no mapped reads found, aborting: " + inputBam.getName());
            return output;
        }

        String locusString = getProvider().getParameterByName(LOCI).extractValue(getPipelineCtx().getJob(), getProvider(), String.class);
        if (locusString == null)
        {
            throw new PipelineJobException("No loci selected");
        }

        String[] loci = locusString.split(";");

        String tcrDBJSON = getProvider().getParameterByName(TCR_DBs).extractValue(getPipelineCtx().getJob(), getProvider(), String.class);
        if (tcrDBJSON == null)
        {
            throw new PipelineJobException("No TCR DBs selected");
        }

        //iterate selected species/loci:
        List<Pair<String, File>> tables = new ArrayList<>();
        JSONArray libraries = new JSONArray(tcrDBJSON);
        for (JSONObject library : libraries.toJSONObjectArray())
        {
            String species = library.getString("species");
            //String locus = library.getString("locus");
            String rowid = String.valueOf(library.get("rowid"));

            MiXCRWrapper mixcr = new MiXCRWrapper(getPipelineCtx().getLogger());
            mixcr.setOutputDir(outputDir);

            List<String> alignParams = new ArrayList<>();
            List<String> assembleParams = new ArrayList<>();
            if (getProvider().getParameterByName(IS_RNA_SEQ).extractValue(getPipelineCtx().getJob(), getProvider(), Boolean.class))
            {
                alignParams.add("-p");
                alignParams.add("rna-seq");
            }

            boolean local = library.optBoolean("local", false);
            if (local)
            {
                alignParams.add("--library");
                alignParams.add("local");
            }

            if (library.optString("additionalParams") != null)
            {
                // -OvParameters.geneFeatureToAlign=VRegion
                for (String s : library.getString("additionalParams").split(";"))
                {
                    alignParams.add(s);
                }
            }

            Integer threads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getJob());
            if (threads != null)
            {
                alignParams.add("-t");
                alignParams.add(threads.toString());

                assembleParams.add("-t");
                assembleParams.add(threads.toString());
            }

            String prefix = FileUtil.getBaseName(inputBam) + "." + rowid + "." + species;
            File clones = mixcr.doAlignmentAndAssemble(forwardFq, reverseFq, prefix, species, alignParams, assembleParams);
            output.addIntermediateFile(clones);

            File alignOut = new File(outputDir, prefix + ".mixcr.aln");
            output.addIntermediateFile(alignOut);

            if (getProvider().getParameterByName(EXPORT_ALIGNMENTS).extractValue(getPipelineCtx().getJob(), getProvider(), Boolean.class, Boolean.FALSE))
            {
                File alignOutput = new File(outputDir, prefix + ".mixcr.alignments");
                mixcr.doExportAlignments(clones, alignOutput);
                output.addOutput(alignOutput, "MiXCR Alignments");
            }

            for (String locus : loci)
            {
                File table = new File(outputDir, prefix + "." + locus + ".mixcr.txt");

                List<String> exportParams = new ArrayList<>();
                Double minCloneFraction = getProvider().getParameterByName(MIN_CLONE_FRACTION).extractValue(getPipelineCtx().getJob(), getProvider(), Double.class, null);
                Integer minCloneReads = getProvider().getParameterByName(MIN_CLONE_READS).extractValue(getPipelineCtx().getJob(), getProvider(), Integer.class, null);

                if (minCloneReads != null)
                {
                    exportParams.add("--minimal-clone-count");
                    exportParams.add(minCloneReads.toString());
                }

                if (minCloneFraction != null)
                {
                    exportParams.add("--minimal-clone-fraction");
                    exportParams.add(minCloneFraction.toString());
                }

                mixcr.doExportClones(clones, table, locus, exportParams);
                tables.add(Pair.of(locus, table));
            }
        }

        File combinedTable = getCombinedTable(outputDir);
        try (PrintWriter writer = PrintWriters.getPrintWriter(combinedTable))
        {
            boolean hasHeader = false;
            for (Pair<String, File> pair : tables)
            {
                String locus = pair.first;
                File f = pair.second;
                try (BufferedReader reader = Readers.getReader(f))
                {
                    String line;
                    int idx = 0;
                    while ((line = reader.readLine()) != null)
                    {
                        idx++;
                        if (idx == 1)
                        {
                            if (!hasHeader)
                            {
                                writer.write("Locus\t" + line);
                                writer.write('\n');
                                hasHeader = true;
                            }
                        }
                        else
                        {
                            writer.write(locus + '\t' + line);
                            writer.write('\n');
                        }
                    }
                }

                f.delete();
            }

            output.addOutput(combinedTable, "MiXCR CDR3 Data");
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return output;
    }

    private File getCombinedTable(File outputDir)
    {
        return new File(outputDir, "mixcr.txt");
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        File table = getCombinedTable(outDir);
        if (!table.exists())
        {
            getPipelineCtx().getLogger().warn("output table does not exist: " + table.getPath());
        }
        else
        {
            getPipelineCtx().getLogger().info("importing results");

        }

        //LaboratoryService.get().saveAssayBatch();

        return null;
    }

    private File getScript(String path) throws PipelineJobException
    {
        Module module = ModuleLoader.getInstance().getModule(TCRdbModule.class);
        FileResource resource = (FileResource)module.getModuleResolver().lookup(Path.parse(path));
        if (resource == null)
            throw new PipelineJobException("Not found: " + path);

        File file = resource.getFile();
        if (!file.exists())
            throw new PipelineJobException("Not found: " + file.getPath());

        return file;
    }

    private boolean hasLines(File f) throws PipelineJobException
    {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(f)), StringUtilsLabKey.DEFAULT_CHARSET));)
        {
            while (reader.readLine() != null)
            {
                return true;
            }

            return false;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private static class MiXCROutput extends DefaultPipelineStepOutput implements AnalysisStep.Output
    {

    }
}
