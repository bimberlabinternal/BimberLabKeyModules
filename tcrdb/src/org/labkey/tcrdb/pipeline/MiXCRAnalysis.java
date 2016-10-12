package org.labkey.tcrdb.pipeline;

import au.com.bytecode.opencsv.CSVReader;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.Readers;
import org.labkey.api.resource.FileResource;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
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
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static String DIFF_LOCI = "diffLoci";
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
                    }}, 2),
                    ToolParameterDescriptor.create(EXPORT_ALIGNMENTS, "Export Alignments", "If checked, MiXCR will also output a text file with the actual alignments produced.  This can be helpful to debug or double check results", "checkbox", new JSONObject()
                    {{

                    }}, false),
                    ToolParameterDescriptor.create(DIFF_LOCI, "Allow Different V/J Loci", "If checked, MiXCR will accept alignments with different loci of V and J genes.  Otheriwse these are discarded.", "checkbox", new JSONObject()
                    {{

                    }}, false),
                    ToolParameterDescriptor.create(IS_RNA_SEQ, "RNA-Seq Data", "If checked, MiXCR settings tailored to RNA-Seq (see -p rna-seq) will be used.", "checkbox", new JSONObject()
                    {{
                        put("checked", true);
                    }}, true),
                    ToolParameterDescriptor.create(TARGET_ASSAY, "Target Assay", "Results will be loaded into this assay.  If no assay is selected, a table will be created with nothing in the DB.", "tcr-assayselectorfield", null, null),
                    ToolParameterDescriptor.create(LOCI, "Loci", "Clones matching the selected loci will be exported.", "tcrdb-locusfield", new JSONObject()
                    {{
                        put("value", "TRA;TRB;TRD;TRG");
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

        wrapper.addToEnvironment("JAVA", SequencePipelineService.get().getJavaFilepath());
        wrapper.addToEnvironment("SAMTOOLS", SequencePipelineService.get().getExeForPackage("SAMTOOLSPATH", "samtools").getPath());
        wrapper.addToEnvironment("PICARD", PicardWrapper.getPicardJar().getPath());
        wrapper.setWorkingDir(outputDir);
        wrapper.execute(Arrays.asList("bash", bamScript.getPath(), inputBam.getPath(), forwardFq.getPath(), reverseFq.getPath()));

        //abort if no reads present
        if (!forwardFq.exists() || !hasLines(forwardFq))
        {
            getPipelineCtx().getLogger().info("no mapped reads found, aborting: " + inputBam.getName());
            if (forwardFq.exists())
            {
                forwardFq.delete();
            }
            return output;
        }
        else
        {
            getPipelineCtx().getLogger().info("calculating FASTQ metrics:");
            Map<String, Object> metricsMap = SequencePipelineService.get().getQualityMetrics(forwardFq, getPipelineCtx().getJob().getLogger());
            for (String metricName : metricsMap.keySet())
            {
                getPipelineCtx().getLogger().debug(metricName + ": " + metricsMap.get(metricName));
            }
        }

        //only add if has reads
        if (reverseFq.exists() && hasLines(reverseFq))
        {
            output.addIntermediateFile(reverseFq, "FASTQ Data");
            SequencePipelineService.get().getQualityMetrics(reverseFq, getPipelineCtx().getJob().getLogger());
        }
        else
        {
            if (reverseFq.exists())
            {
                getPipelineCtx().getLogger().info("deleting empty file: " + reverseFq.getName());
                reverseFq.delete();
            }
            else
            {
                getPipelineCtx().getLogger().info("no reverse reads found");
            }

            reverseFq = null;
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
        Map<Integer, Map<String, List<File>>> tables = new HashMap<>();
        JSONArray libraries = new JSONArray(tcrDBJSON);
        for (JSONObject library : libraries.toJSONObjectArray())
        {
            String species = library.getString("species");
            Integer rowid = library.optInt("rowid");

            MiXCRWrapper mixcr = new MiXCRWrapper(getPipelineCtx().getLogger());
            mixcr.setOutputDir(outputDir);
            String javaDir = StringUtils.trimToNull(System.getenv("JAVA_HOME"));
            if (javaDir != null)
            {
                getPipelineCtx().getLogger().debug("setting JAVA_HOME: " + javaDir);
                mixcr.addToEnvironment("JAVA_HOME", javaDir);
            }
            else
            {
                getPipelineCtx().getLogger().debug("JAVA_HOME not set");
            }

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

            if (getProvider().getParameterByName(DIFF_LOCI).extractValue(getPipelineCtx().getJob(), getProvider(), Boolean.class))
            {
                //TODO: v2.0:
                //alignParams.add("-OallowChimeras=true");
                alignParams.add("--diff-loci");
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
            output.addOutput(clones, "MiXCR Clones File");

            output.addOutput(new File(outputDir, prefix + ".mixcr.vdjca"), "MiXCR VDJ Alignment");
            output.addOutput(new File(outputDir, prefix + ".mixcr.index"), "MiXCR VDJ Index File 1");
            output.addOutput(new File(outputDir, prefix + ".mixcr.index.p"), "MiXCR VDJ Index File 2");

            File alignPartialOutput1 = new File(outputDir, prefix + ".mixcr.partial.1.vdjca");
            output.addOutput(alignPartialOutput1, "MiXCR VDJ Alignment, Recovery Step 1");
            File alignPartialOutput2 = new File(outputDir, prefix + ".mixcr.partial.2.vdjca");
            output.addOutput(alignPartialOutput2, "MiXCR VDJ Alignment, Recovery Step 2");

            if (getProvider().getParameterByName(EXPORT_ALIGNMENTS).extractValue(getPipelineCtx().getJob(), getProvider(), Boolean.class, Boolean.FALSE))
            {
                if (alignPartialOutput2.length() >= 20)
                {
                    File alignExport = new File(outputDir, prefix + ".mixcr.alignments.txt");
                    getPipelineCtx().getLogger().debug("output file size: " + alignPartialOutput2.length());
                    mixcr.doExportAlignments(alignPartialOutput2, alignExport);
                    output.addOutput(alignExport, "MiXCR Alignments");
                }
                else
                {
                    getPipelineCtx().getLogger().info("output too small, skipping export");
                }
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
                if (!tables.containsKey(rowid))
                {
                    tables.put(rowid, new HashMap<>());
                }

                if (!tables.get(rowid).containsKey(locus))
                {
                    tables.get(rowid).put(locus, new ArrayList<>());
                }

                tables.get(rowid).get(locus).add(table);
            }
        }

        File combinedTable = getCombinedTable(outputDir);
        try (PrintWriter writer = PrintWriters.getPrintWriter(combinedTable))
        {
            boolean hasHeader = false;
            for (Integer libraryId : tables.keySet())
            {
                Map<String, List<File>> tablesForLocus = tables.get(libraryId);
                for (String locus : tablesForLocus.keySet())
                {
                    for (File f : tablesForLocus.get(locus))
                    {
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
                                        writer.write("LibraryId\tLocus\t" + line);
                                        writer.write('\n');
                                        hasHeader = true;
                                    }
                                }
                                else
                                {
                                    writer.write(String.valueOf(libraryId) + '\t' + locus + '\t' + line);
                                    writer.write('\n');
                                }
                            }
                        }

                        f.delete();
                    }
                }
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

    private List<String> FIELDS = Arrays.asList(
        "libraryId",
        "locus",
        "cloneId",
        "vHit",
        "dHit",
        "jHit",
        "cHit",
        "CDR3",
        "CDR3FromLeft",
        "CDR3FromRight",
        "length",
        "count",
        "fraction",
        "targets",
        "vHits",
        "dHits",
        "jHits",
        "cHits",
        "vFamily",
        "vFamilies",
        "dFamily",
        "dFamilies",
        "jFamily",
        "jFamilies",
        "vBestIdentityPercent",
        "dBestIdentityPercent",
        "jBestIdentityPercent",
        "cdr3_nt",
        "cdr3_qual"
        //"cloneSequence",
        //"nSeqVDJTranscript"
    );

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        File table = getCombinedTable(outDir);
        if (!table.exists())
        {
            getPipelineCtx().getLogger().warn("output table does not exist: " + table.getPath());
            return null;
        }
        else
        {
            getPipelineCtx().getLogger().info("importing results from: " + table.getPath());
        }

        if (!hasLines(table))
        {
            getPipelineCtx().getLogger().info("no rows in table, skipping: " + table.getPath());
            return null;
        }

        Integer assayId = getProvider().getParameterByName(TARGET_ASSAY).extractValue(getPipelineCtx().getJob(), getProvider(), Integer.class);
        if (assayId == null)
        {
            getPipelineCtx().getLogger().info("No assay selected, will not import");
            return null;
        }

        ExpProtocol protocol = ExperimentService.get().getExpProtocol(assayId);
        if (protocol == null)
        {
            throw new PipelineJobException("Unable to find protocol: " + assayId);
        }

        ViewBackgroundInfo info = getPipelineCtx().getJob().getInfo();
        ViewContext vc = ViewContext.getMockViewContext(info.getUser(), info.getContainer(), info.getURL(), false);

        getPipelineCtx().getLogger().info("importing from table: " + table.getPath());

        List<Map<String, Object>> rows = new ArrayList<>();
        try (CSVReader reader = new CSVReader(Readers.getReader(table), '\t'))
        {
            int lineNo = 0;
            String[] line;
            while ((line = reader.readNext()) != null)
            {
                lineNo++;
                if (lineNo == 1)
                {
                    continue;
                }

                Map<String, Object> row = new CaseInsensitiveHashMap<>();
                if (model.getReadset() != null)
                {
                    Readset rs = SequenceAnalysisService.get().getReadset(model.getReadset(), getPipelineCtx().getJob().getUser());
                    if (rs != null)
                    {
                        row.put("sampleName", rs.getName());
                        row.put("subjectid", rs.getSubjectId());
                        row.put("date", rs.getSampleDate());
                    }
                    else
                    {
                        throw new PipelineJobException("Unable to find readset: " + model.getReadset());
                    }
                }
                else
                {
                    row.put("sampleName", "Analysis Id: " + model.getRowId());
                }

                row.putIfAbsent("date", new Date());
                row.put("sampleType", null);
                row.put("category", null);
                row.put("stimulation", null);

                if (line.length != (FIELDS.size() + 2))  //this includes 2 fields not imported into DB
                {
                    getPipelineCtx().getLogger().warn(lineNo + ": line length not " + FIELDS.size() + ".  was: " + line.length);
                    getPipelineCtx().getLogger().warn(StringUtils.join(line, ";"));
                }

                for (int i=0;i<FIELDS.size();i++)
                {
                    row.put(FIELDS.get(i), line[i]);
                }

                //try to reconcile CDR3 translations:
                if (row.get("CDR3") != null)
                {
                    if (row.get("CDR3FromLeft") != null && !row.get("CDR3").equals(row.get("CDR3FromLeft")))
                    {
                        getPipelineCtx().getLogger().warn("CDR3 translation (" + row.get("CDR3") +") does not match translation from left: " + row.get("CDR3FromLeft"));
                    }

                    if (row.get("CDR3FromRight") != null && !row.get("CDR3").equals(row.get("CDR3FromRight")))
                    {
                        getPipelineCtx().getLogger().warn("CDR3 translation (" + row.get("CDR3") +") does not match translation from right: " + row.get("CDR3FromRight"));
                    }
                }

                row.put("alignmentId", model.getAlignmentFile());
                row.put("analysisId", model.getRowId());

                Integer runId = SequencePipelineService.get().getExpRunIdForJob(getPipelineCtx().getJob());
                row.put("pipelineRunId", runId);

                rows.add(row);
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        try
        {
            JSONObject runProps = new JSONObject();
            runProps.put("performedby", getPipelineCtx().getJob().getUser().getDisplayName(getPipelineCtx().getJob().getUser()));
            runProps.put("assayName", "MiXCR");
            runProps.put("Name", "Analysis: " + model.getAnalysisId());

            JSONObject json = new JSONObject();
            json.put("Run", runProps);

            File assayTmp = new File(table.getParentFile(), "mixcr-assay-upload.txt");
            if (assayTmp.exists())
            {
                assayTmp.delete();
            }

            getPipelineCtx().getLogger().info("total rows imported: " + rows.size());
            LaboratoryService.get().saveAssayBatch(rows, json, assayTmp, vc, AssayService.get().getProvider(protocol), protocol);
        }
        catch (ValidationException e)
        {
            throw new PipelineJobException(e);
        }

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
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(f.getName().endsWith(".gz") ? new GZIPInputStream(new FileInputStream(f)) : new FileInputStream(f), StringUtilsLabKey.DEFAULT_CHARSET));)
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
