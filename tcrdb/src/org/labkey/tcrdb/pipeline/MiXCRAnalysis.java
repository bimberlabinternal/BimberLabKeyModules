package org.labkey.tcrdb.pipeline;

import au.com.bytecode.opencsv.CSVReader;
import htsjdk.samtools.fastq.FastqReader;
import htsjdk.samtools.fastq.FastqWriter;
import htsjdk.samtools.fastq.FastqWriterFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
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
import org.labkey.api.sequenceanalysis.model.ReadData;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAnalysisStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.DefaultPipelineStepOutput;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PreprocessingStep;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.PicardWrapper;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
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
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private static final String TCR_DBs = "tcrDB";
    private static final String FLAG_MISSENSE = "flagMissense";
    private static final String MIN_CLONE_FRACTION = "minCloneFraction";
    private static final String MIN_CLONE_READS = "minCloneReads";
    private static final String EXPORT_ALIGNMENTS = "exportAlignments";
    private static final String CLONES_FILE = "MiXCR Clones File";
    private static final String FINAL_VDJ_FILE = "MiXCR VDJ Alignment";
    private static final String DIFF_LOCI = "diffLoci";
    private static final String IS_RNA_SEQ = "isRnaSeq";
    private static final String TARGET_ASSAY = "targetAssay";
    private static final String LOCI = "loci";

    public static class Provider extends AbstractAnalysisStepProvider<MiXCRAnalysis>
    {
        public Provider()
        {
            super("MiXCR", "MiXCR", null, "Any reads in the BAM file that are mapped will be fed to MiXCR for TCR sequence analysis.  Results will be imported into the selected assay.  The analysis expects the reads are aligned to some type of TCR sequence DB, which serves as a filter step to enrich for TCR-specific reads.", Arrays.asList(
                    ToolParameterDescriptor.create(TCR_DBs, "TCR DB(s)", "The sequence DB(s), usually species, to be used for alignment.", "tcr-libraryfield", new JSONObject()
                    {{
                        put("allowBlank", false);
                    }}, null),
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
                        put("value", "ALL");
                    }}, true),
                    ToolParameterDescriptor.create(FLAG_MISSENSE, "Flag Missense CDR3", "If checked, if a sample has duplicate CDR3 clones from the same locus, and and one of these is missense, that clone will be flagged and excluded from many reports.", "checkbox", new JSONObject()
                    {{
                        put("checked", true);
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
        File forwardFq;
        File reverseFq;
        if (rs.getReadData() != null)
        {
            getPipelineCtx().getLogger().debug("using raw readset data instead of BAM");
            if (rs.getReadData().size() == 1)
            {
                ReadData rd = rs.getReadData().get(0);
                forwardFq = rd.getFile1();
                reverseFq = rd.getFile2();
            }
            else
            {
                getPipelineCtx().getLogger().debug("concatenating multiple ReadData together into single FASTQ");

                forwardFq = new File(outputDir, FileUtil.getBaseName(inputBam) + "-R1.fastq.gz");
                output.addIntermediateFile(forwardFq, "FASTQ Data");

                reverseFq = new File(outputDir, FileUtil.getBaseName(inputBam) + "-R2.fastq.gz");
                output.addIntermediateFile(reverseFq, "FASTQ Data");

                FastqWriterFactory fact = new FastqWriterFactory();
                fact.setUseAsyncIo(true);
                try (FastqWriter w1 = fact.newWriter(forwardFq);FastqWriter w2 = fact.newWriter(reverseFq))
                {
                    for (ReadData rd : rs.getReadData())
                    {
                        try (FastqReader reader = new FastqReader(rd.getFile1()))
                        {
                            while (reader.hasNext())
                            {
                                w1.write(reader.next());
                            }
                        }

                        if (rd.getFile2() != null)
                        {
                            try (FastqReader reader = new FastqReader(rd.getFile2()))
                            {
                                while (reader.hasNext())
                                {
                                    w2.write(reader.next());
                                }
                            }
                        }
                    }
                }

                if (!hasLines(reverseFq))
                {
                    getPipelineCtx().getLogger().debug("deleting empty file: " + reverseFq.getPath());
                    reverseFq.delete();
                    reverseFq = null;
                }
            }

            //now trim:
            List<String> trimParams = Arrays.asList("MAXINFO:50:0.9", "MINLEN:50");
            PreprocessingStep.Output trimOutput = SequencePipelineService.get().simpleTrimFastqPair(forwardFq, reverseFq, trimParams, getPipelineCtx().getLogger());
            for (File f : trimOutput.getIntermediateFiles())
            {
                output.addIntermediateFile(f);
            }

            forwardFq = trimOutput.getProcessedFastqFiles().first;
            reverseFq = trimOutput.getProcessedFastqFiles().second;
        }
        else
        {
            SimpleScriptWrapper wrapper = new SimpleScriptWrapper(getPipelineCtx().getLogger());

            File bamScript = getScript("external/exportMappedReads.sh");
            forwardFq = new File(outputDir, FileUtil.getBaseName(inputBam) + "-R1.fastq.gz");
            output.addIntermediateFile(forwardFq, "FASTQ Data");
            reverseFq = new File(outputDir, FileUtil.getBaseName(inputBam) + "-R2.fastq.gz");

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
        }

        //now run mixcr
        String locusString = getProvider().getParameterByName(LOCI).extractValue(getPipelineCtx().getJob(), getProvider(), String.class);
        if (locusString == null)
        {
            locusString = "ALL";
        }

        String[] loci = locusString.split(";");

        String tcrDBJSON = getProvider().getParameterByName(TCR_DBs).extractValue(getPipelineCtx().getJob(), getProvider(), String.class);
        if (tcrDBJSON == null)
        {
            throw new PipelineJobException("No TCR DBs selected");
        }

        String version = new MiXCRWrapper(getPipelineCtx().getLogger()).getVersionString();

        //iterate selected species/loci:
        Map<Integer, Map<String, Map<String, List<File>>>> tables = new HashMap<>();
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

            String libraryName = StringUtils.trimToNull(library.optString("libraryName"));
            if (libraryName != null)
            {
                alignParams.add("--library");
                alignParams.add(libraryName);
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
                alignParams.add("-OallowChimeras=true");
            }

            Integer threads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getJob().getLogger());
            if (threads != null)
            {
                alignParams.add("-t");
                alignParams.add(threads.toString());

                assembleParams.add("-t");
                assembleParams.add(threads.toString());
            }

            String prefix = getOutputPrefix(FileUtil.getBaseName(inputBam), String.valueOf(rowid), species);
            File clones = mixcr.doAlignmentAndAssemble(forwardFq, reverseFq, prefix, species, alignParams, assembleParams);
            output.addOutput(clones, CLONES_FILE);

            output.addIntermediateFile(new File(outputDir, prefix + ".align.vdjca.gz"), "MiXCR VDJ Alignment");

            File alignPartialOutput1 = new File(outputDir, prefix + ".assemblePartial_1.vdjca.gz");
            output.addIntermediateFile(alignPartialOutput1, "MiXCR VDJ Alignment, Recovery Step 1");
            File alignPartialOutput2 = new File(outputDir, getFinalVDJFileName(prefix));
            //output.addIntermediateFile(alignPartialOutput2, "MiXCR VDJ Alignment, Recovery Step 2");

            File finalVDJ = alignPartialOutput2;
            output.addOutput(finalVDJ, FINAL_VDJ_FILE);

            output.addOutput(new File(outputDir, MiXCRAnalysis.getClonesFileName(prefix) + ".index"), "MiXCR VDJ Index File 1");
            output.addOutput(new File(outputDir, MiXCRAnalysis.getClonesFileName(prefix) + ".index.p"), "MiXCR VDJ Index File 2");

            if (getProvider().getParameterByName(EXPORT_ALIGNMENTS).extractValue(getPipelineCtx().getJob(), getProvider(), Boolean.class, Boolean.FALSE))
            {
                if (finalVDJ.length() >= 20)
                {
                    File alignExport = new File(outputDir, prefix + ".alignments.txt");
                    getPipelineCtx().getLogger().debug("output file size: " + finalVDJ.length());
                    mixcr.doExportAlignments(finalVDJ, alignExport, null);
                    output.addOutput(alignExport, "MiXCR Alignments");

                    //TODO: consider calculating stats on alignments
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

                if (!tables.get(rowid).containsKey(species))
                {
                    tables.get(rowid).put(species, new HashMap<>());
                }

                if (!tables.get(rowid).get(species).containsKey(locus))
                {
                    tables.get(rowid).get(species).put(locus, new ArrayList<>());
                }

                tables.get(rowid).get(species).get(locus).add(table);
            }
        }

        File combinedTable = getCombinedTable(outputDir);
        try (PrintWriter writer = PrintWriters.getPrintWriter(combinedTable))
        {
            boolean hasHeader = false;
            for (Integer libraryId : tables.keySet())
            {
                Map<String, Map<String, List<File>>> tablesForSpecies = tables.get(libraryId);
                for (String species : tablesForSpecies.keySet())
                {
                    Map<String, List<File>> tablesForLocus = tablesForSpecies.get(species);
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
                                            writer.write("LibraryId\tMiXCR_Version\tSpecies\tLocus\t" + line);
                                            writer.write('\n');
                                            hasHeader = true;
                                        }
                                    }
                                    else
                                    {
                                        //attempt to infer locus based on the gene hits
                                        String[] fields = line.split("\t");
                                        Set<String> chains = new HashSet<>();
                                        for (String fn : Arrays.asList("vHit", "dHit", "jHit", "cHit"))
                                        {
                                            String val = StringUtils.trimToNull(fields[FIELDS.indexOf(fn)]);
                                            if (val == null)
                                            {
                                                continue;
                                            }

                                            for (String chain : Arrays.asList("TRA", "TRB", "TRD", "TRG"))
                                            {
                                                if (val.startsWith(chain))
                                                {
                                                    chains.add(chain);
                                                    break;
                                                }
                                            }
                                        }

                                        String inferredLocus = StringUtils.join(chains, ";");

                                        String rowLocus = locus;
                                        if ("ALL".equals(locus) || "TCR".equals(locus))
                                        {
                                            rowLocus = inferredLocus;
                                        }
                                        else if (!locus.equals(inferredLocus))
                                        {
                                            getPipelineCtx().getLogger().warn("Exported locus does not match the locus inferred by the gene hits.  exported: " + locus + ", inferred: " + inferredLocus);
                                        }

                                        writer.write(String.valueOf(libraryId) + '\t' + species + '\t' + rowLocus + '\t' + version + '\t' + line);
                                        writer.write('\n');
                                    }
                                }
                            }

                            f.delete();
                        }
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
            "species",
            "locus",
            "mixcrVersion",
            "cloneId",
            "vHit",
            "dHit",
            "jHit",
            "cHit",
            "CDR3",
            "length",
            "count",
            "fraction",
            "targets",
            "vHits",
            "dHits",
            "jHits",
            "cHits",
            "vGene",
            "vGenes",
            "dGene",
            "dGenes",
            "jGene",
            "jGenes",
            "cGene",
            "cGenes",
            "vBestIdentityPercent",
            "dBestIdentityPercent",
            "jBestIdentityPercent",
            "cdr3_nt",
            "cdr3_qual",
            "cloneSequence"
            //"nSeqVDJTranscript"
    );

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        Integer runId = SequencePipelineService.get().getExpRunIdForJob(getPipelineCtx().getJob());
        ExpRun run = ExperimentService.get().getExpRun(runId);

        List<? extends ExpData> cloneDatas = run.getInputDatas(CLONES_FILE, ExpProtocol.ApplicationType.ExperimentRunOutput);
        List<? extends ExpData> vdjDatas = run.getInputDatas(FINAL_VDJ_FILE, ExpProtocol.ApplicationType.ExperimentRunOutput);

        File table = getCombinedTable(outDir);
        if (!table.exists())
        {
            getPipelineCtx().getLogger().warn("output table does not exist: " + table.getPath());
            return null;
        }
        else if (!SequencePipelineService.get().hasMinLineCount(table, 2))
        {
            getPipelineCtx().getLogger().warn("insufficient lines in output table, skipping: " + table.getPath());
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

        getPipelineCtx().getLogger().info("importing assay results from table: " + table.getPath());

        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> mixcrVersions = new HashSet<>();
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
                //round to day
                row.put("date", DateUtils.truncate(row.get("date"), Calendar.DATE));

                row.put("sampleType", null);
                row.put("category", null);
                row.put("stimulation", null);

                if (line.length != (FIELDS.size() + 1))  //this includes one additional field appended to the end
                {
                    getPipelineCtx().getLogger().warn(lineNo + ": line length not " + (FIELDS.size() + 1) + ".  was: " + line.length);
                    getPipelineCtx().getLogger().warn(StringUtils.join(line, ";"));
                }

                for (int i = 0; i < FIELDS.size(); i++)
                {
                    row.put(FIELDS.get(i), line[i]);
                }

                if (row.get("mixcrVersion") != null)
                {
                    mixcrVersions.add(row.get("mixcrVersion").toString());
                }

                String cloneFileName = getClonesFileName(getOutputPrefix(FileUtil.getBaseName(inputBam), line[FIELDS.indexOf("libraryId")], line[FIELDS.indexOf("species")]));
                ExpData clonesFile = null;
                for (ExpData d : cloneDatas)
                {
                    if (cloneFileName.equals(d.getFile().getName()))
                    {
                        clonesFile = d;
                        break;
                    }
                }

                if (clonesFile == null)
                {
                    getPipelineCtx().getLogger().warn("unable to find clones file, expected: " + cloneFileName);
                }
                else
                {
                    row.put("clonesFile", clonesFile.getRowId());
                }

                String vdjFileName = getFinalVDJFileName(getOutputPrefix(FileUtil.getBaseName(inputBam), line[FIELDS.indexOf("libraryId")], line[FIELDS.indexOf("species")]));
                ExpData vdjFile = null;
                for (ExpData d : vdjDatas)
                {
                    if (vdjFileName.equals(d.getFile().getName()))
                    {
                        vdjFile = d;
                        break;
                    }
                }

                if (vdjFile == null)
                {
                    getPipelineCtx().getLogger().warn("unable to find VDJ file, expected: " + vdjFileName);
                }
                else
                {
                    row.put("vdjFile", vdjFile.getRowId());
                }

                if (row.get("CDR3") != null && row.get("CDR3").toString().contains("_") || row.get("CDR3").toString().contains("*"))
                {
                    String msg = "Frameshift or stop codon";
                    getPipelineCtx().getLogger().warn(msg);
                    row.put("comment", msg);
                }

                row.put("alignmentId", model.getAlignmentFile());
                row.put("analysisId", model.getRowId());

                row.put("pipelineRunId", runId);

                rows.add(row);
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        //inspect for TRD, redundant w/ TRA:
        List<String> runComments = new ArrayList<>();
        rows = inspectAndRemoveDuplicateTRD(rows, runComments);

        if (getProvider().getParameterByName(FLAG_MISSENSE).extractValue(getPipelineCtx().getJob(), getProvider(), Boolean.class, false))
        {
            getPipelineCtx().getLogger().debug("will flag the frameshifted CDR3s from any library/locus that also has a non-frameshift CDR3");
            Map<String, Pair<List<Map<String, Object>>, List<Map<String, Object>>>> resultsByLocus = new HashMap<>();
            for (Map<String, Object> row : rows)
            {
                String key = row.get("libraryId") + "-" + row.get("locus");
                if (!resultsByLocus.containsKey(key))
                {
                    resultsByLocus.put(key, Pair.of(new ArrayList<>(), new ArrayList<>()));
                }

                boolean isFrameshift = String.valueOf(row.get("CDR3")).contains("_") || String.valueOf(row.get("CDR3")).contains("*");
                if (isFrameshift)
                {
                    resultsByLocus.get(key).first.add(row);
                }
                else
                {
                    resultsByLocus.get(key).second.add(row);
                }
            }

            List<Map<String, Object>> newRows = new ArrayList<>();
            for (String key : resultsByLocus.keySet())
            {
                //if we have any non-frameshift from this library/locus, flag all frameshifted CDR3s
                if (!resultsByLocus.get(key).second.isEmpty() && !resultsByLocus.get(key).first.isEmpty())
                {
                    getPipelineCtx().getLogger().debug("disabling " + resultsByLocus.get(key).first.size() + " frameshifted CDR3s: " + key);
                    for (Map<String, Object> row : resultsByLocus.get(key).first)
                    {
                        row.put("disabled", true);
                        String comment = "Sample has non-frameshift CDR3 for locus";
                        row.put("comment", row.get("comment") != null ? row.get("comment") + "; " + comment : comment);
                    }
                }

                newRows.addAll(resultsByLocus.get(key).first);
                newRows.addAll(resultsByLocus.get(key).second);
            }

            rows = newRows;
        }

        try
        {
            JSONObject runProps = new JSONObject();
            runProps.put("performedby", getPipelineCtx().getJob().getUser().getDisplayName(getPipelineCtx().getJob().getUser()));
            runProps.put("assayName", (mixcrVersions.isEmpty() ? "MiXCR" : mixcrVersions.iterator().next()));
            runProps.put("Name", "Analysis: " + model.getAnalysisId());
            if (!runComments.isEmpty())
            {
                runProps.put("runComments", StringUtils.join(runComments, ", "));
            }

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

    private List<Map<String, Object>> inspectAndRemoveDuplicateTRD(List<Map<String, Object>> rows, List<String> runComments)
    {
        getPipelineCtx().getLogger().info("inspecting for redundant TRA/TRD calls");
        Map<String, Set<String>> resultsByLocus = new HashMap<>();
        for (Map<String, Object> row : rows)
        {
            String key = row.get("libraryId") + "-" + row.get("locus");
            if (!resultsByLocus.containsKey(key))
            {
                resultsByLocus.put(key, new CaseInsensitiveHashSet());
            }

            resultsByLocus.get(key).add(String.valueOf(row.get("CDR3")));
        }

        List<Map<String, Object>> newRows = new ArrayList<>();
        int trdSkipped = 0;
        for (Map<String, Object> row : rows)
        {
            if ("TRD".equals(row.get("locus")))
            {
                String key = row.get("libraryId") + "-TRA";
                if (resultsByLocus.containsKey(key) && resultsByLocus.get(key).contains(String.valueOf(row.get("CDR3"))))
                {
                    getPipelineCtx().getLogger().debug("skipping redundant TRD: " + row.get("CDR3"));
                    trdSkipped++;
                }
                else
                {
                    newRows.add(row);
                }
            }
            else
            {
                newRows.add(row);
            }
        }

        if (trdSkipped > 0)
        {
            runComments.add("TRD records skipped due to redundancy with TRA: " + trdSkipped);
        }

        return newRows;
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

    public static String getClonesFileName(String outputPrefix)
    {
        return outputPrefix + ".clones";
    }

    public static String getFinalVDJFileName(String outputPrefix)
    {
        return outputPrefix + ".assemblePartial_2.vdjca.gz";
    }

    private String getOutputPrefix(String basename, String libraryId, String species)
    {
        return basename + "." + libraryId + "." + species;
    }
}
