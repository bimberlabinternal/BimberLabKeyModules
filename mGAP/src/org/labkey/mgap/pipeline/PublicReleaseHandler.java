package org.labkey.mgap.pipeline;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.IOUtil;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Results;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractGatkWrapper;
import org.labkey.api.sequenceanalysis.run.GeneToNameTranslator;
import org.labkey.api.sequenceanalysis.run.SelectVariantsWrapper;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.mgap.mGAPModule;
import org.labkey.mgap.mGAPSchema;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by bimber on 5/2/2017.
 */
public class PublicReleaseHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private final FileType _vcfType = new FileType(Arrays.asList(".vcf"), ".vcf", false, FileType.gzSupportLevel.SUPPORT_GZ);

    public PublicReleaseHandler()
    {
        super(ModuleLoader.getInstance().getModule(mGAPModule.class), "Create mGAP Release", "This will prepare an input VCF for use as an mGAP public release.  This will optionally include: removing excess annotations and program records, limiting to SNVs (optional) and removing genotype data (optional).  If genotypes are retained, the subject names will be checked for mGAP aliases and replaced as needed.", null, Arrays.asList(
                ToolParameterDescriptor.create("releaseVersion", "Version", "This string will be used as the version when published.", "textfield", new JSONObject(){{
                    put("allowBlank", false);
                }}, null),
                ToolParameterDescriptor.create("removeAnnotations", "Remove Most Annotations", "If selected, most annotations and extraneous information will be removed.  This is both to trim down the size of the public VCF and to shield some information.", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, null),
                ToolParameterDescriptor.create("sitesOnly", "Omit Genotypes", "If selected, genotypes will be omitted and a VCF with only the first 8 columns will be produced.", "checkbox", new JSONObject(){{
                    put("checked", false);
                }}, null),
                ToolParameterDescriptor.create("snvOnly", "Limit To SNVs", "If selected, only variants of the type SNV will be included.", "checkbox", new JSONObject()
                {{
                    put("checked", false);
                }}, true),
                ToolParameterDescriptor.createExpDataParam("gtfFile", "GTF File", "The gene file used to create these annotations.", "sequenceanalysis-genomefileselectorfield", new JSONObject()
                {{
                    put("extensions", Arrays.asList("gtf"));
                    put("width", 400);
                    put("allowBlank", false);
                }}, null),
                ToolParameterDescriptor.create("variantTableOnly", "Variant Table Only", "If selected, the input VCF will be used as-is, and the only output will be the table of significant variants.  This was created mostly for testing purposes.", "checkbox", new JSONObject()
                {{
                    put("checked", false);
                }}, false),
                ToolParameterDescriptor.create("testOnly", "Test Only", "If selected, the various files will be created, but a record will not be created in the relases table, meaning it will not be synced to mGAP.", "checkbox", new JSONObject()
                {{
                    put("checked", false);
                }}, false)
        ));
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && o.getFile().exists() && _vcfType.isType(o.getFile());
    }

    @Override
    public List<String> validateParameters(JSONObject params)
    {
        return null;
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
    public SequenceOutputProcessor getProcessor()
    {
        return new Processor();
    }

    public static class Processor implements SequenceOutputProcessor
    {
        public Processor()
        {

        }

        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            File outputFile = getSampleNameFile(((FileAnalysisJobSupport)job).getAnalysisDirectory());
            job.getLogger().debug("caching mGAP aliases to file: " + outputFile.getPath());

            Map<String, String> sampleNameMap = new HashMap<>();
            for (SequenceOutputFile so : inputFiles)
            {
                try
                {
                    SequenceAnalysisService.get().ensureVcfIndex(so.getFile(), job.getLogger());
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                try (VCFFileReader reader = new VCFFileReader(so.getFile()))
                {
                    VCFHeader header = reader.getFileHeader();
                    TableInfo ti = QueryService.get().getUserSchema(job.getUser(), (job.getContainer().isWorkbook() ? job.getContainer().getParent() : job.getContainer()), mGAPSchema.NAME).getTable(mGAPSchema.TABLE_ANIMAL_MAPPING);
                    TableSelector ts = new TableSelector(ti, PageFlowUtil.set("subjectname", "externalAlias"), new SimpleFilter(FieldKey.fromString("subjectname"), header.getSampleNamesInOrder(), CompareType.IN), null);
                    ts.forEachResults(new Selector.ForEachBlock<Results>()
                    {
                        @Override
                        public void exec(Results rs) throws SQLException
                        {
                            sampleNameMap.put(rs.getString(FieldKey.fromString("subjectname")), rs.getString(FieldKey.fromString("externalAlias")));
                        }
                    });

                    Set<String> sampleNames = new HashSet<>(header.getSampleNamesInOrder());
                    sampleNames.removeAll(sampleNameMap.keySet());
                    if (!sampleNames.isEmpty())
                    {
                        throw new PipelineJobException("mGAP Aliases were not found for all IDs.  Missing: " + StringUtils.join(sampleNames, ", "));
                    }
                }
            }

            job.getLogger().info("total sample names to alias: " + sampleNameMap.size());
            try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(outputFile), '\t', CSVWriter.NO_QUOTE_CHARACTER))
            {
                for (String name : sampleNameMap.keySet())
                {
                    writer.writeNext(new String[]{name, sampleNameMap.get(name)});
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        private File getSampleNameFile(File outputDir)
        {
            return new File(outputDir, "sampleMapping.txt");
        }

        @Override
        public void complete(PipelineJob job, List<SequenceOutputFile> inputs, List<SequenceOutputFile> outputsCreated) throws PipelineJobException
        {
            if (outputsCreated.isEmpty())
            {
                job.getLogger().error("no outputs found");
            }

            Map<String, SequenceOutputFile> outputVCFMap = new HashMap<>();
            Map<String, SequenceOutputFile> outputTableMap = new HashMap<>();
            for (SequenceOutputFile so : outputsCreated)
            {
                if (so.getRowid() == null || so.getRowid() == 0)
                {
                    throw new PipelineJobException("No rowId found for sequence output");
                }

                if (so.getName().endsWith("Table"))
                {
                    String name = so.getName().replaceAll(" Variant Table", "");
                    outputTableMap.put(name, so);
                }
                else
                {
                    outputVCFMap.put(so.getName(), so);
                }
            }

            //save all DB inserts for list:
            List<Map<String, Object>> variantReleaseRows = new ArrayList<>();
            List<Map<String, Object>> variantTableRows = new ArrayList<>();
            List<Map<String, Object>> releaseStatsRows = new ArrayList<>();
            boolean testOnly = StringUtils.isEmpty(job.getParameters().get("testOnly")) ? false : ConvertHelper.convert(job.getParameters().get("testOnly"), boolean.class);

            for (String release : outputVCFMap.keySet())
            {
                SequenceOutputFile so = outputVCFMap.get(release);
                SequenceOutputFile so2 = outputTableMap.get(release);
                if (so2 == null)
                {
                    throw new PipelineJobException("Unable to find table output for release: " + release);
                }

                //find basic stats:
                job.getLogger().info("inspecting file: " + so.getName());
                int totalSubjects;
                long totalVariants = 0;
                try (VCFFileReader reader = new VCFFileReader(so.getFile()))
                {
                    totalSubjects = reader.getFileHeader().getSampleNamesInOrder().size();
                    try (CloseableIterator<VariantContext> it = reader.iterator())
                    {
                        while (it.hasNext())
                        {
                            VariantContext vc = it.next();
                            if (vc.isFiltered())
                            {
                                throw new PipelineJobException("The published VCF should not contain filtered sites");
                            }

                            totalVariants++;
                            if (totalVariants % 1000000 == 0)
                            {
                                job.getLogger().info("processed " + totalVariants + " sites");
                            }
                        }
                    }
                }

                if (totalSubjects == 0)
                {
                    boolean sitesOnly = Boolean.parseBoolean(job.getParameters().get("sitesOnly"));
                    if (sitesOnly)
                    {
                        job.getLogger().info("attempting to infer total subjects from original VCF");
                        File originalVCF = inputs.get(0).getFile();
                        try (VCFFileReader reader = new VCFFileReader(originalVCF))
                        {
                            totalSubjects = reader.getFileHeader().getSampleNamesInOrder().size();
                        }
                    }
                }

                //actually create outputfile
                Map<String, Object> row = new CaseInsensitiveHashMap<>();
                row.put("version", job.getParameters().get("releaseVersion"));
                row.put("releaseDate", new Date());
                row.put("vcfId", so.getRowid());
                row.put("variantTable", so2.getRowid());
                row.put("genomeId", so.getLibrary_id());
                row.put("totalSubjects", totalSubjects);
                row.put("totalVariants", totalVariants);
                String guid = new GUID().toString();
                row.put("objectId", guid);

                try
                {
                    if (!testOnly){
                        variantReleaseRows.add(row);

                        File variantTable = so2.getFile();
                        if (variantTable.exists())
                        {
                            try (CSVReader reader = new CSVReader(IOUtil.openFileForBufferedReading(variantTable), '\t'))
                            {
                                String[] line;
                                int lineNo = 0;
                                while ((line = reader.readNext()) != null)
                                {
                                    lineNo++;
                                    if (lineNo == 1)
                                    {
                                        continue; //header
                                    }

                                    Map<String, Object> map = new CaseInsensitiveHashMap<>();
                                    map.put("releaseId", guid);
                                    map.put("contig", line[0]);
                                    map.put("position", line[1]);
                                    map.put("reference", line[2]);
                                    map.put("allele", line[3]);
                                    map.put("source", line[4]);
                                    map.put("reason", line[5]);
                                    map.put("description", line[6]);
                                    map.put("overlappingGenes", line[7]);
                                    map.put("objectId", new GUID().toString());

                                    variantTableRows.add(map);
                                }
                            }
                        }
                        else
                        {
                            job.getLogger().error("unable to find release stats file: " + variantTable.getPath());
                        }

                        File releaseStats = new File(so.getFile().getParentFile(), SequenceAnalysisService.get().getUnzippedBaseName(so.getFile().getName()) + ".summaryByField.txt");
                        if (releaseStats.exists())
                        {
                            try (CSVReader reader = new CSVReader(IOUtil.openFileForBufferedReading(releaseStats), '\t'))
                            {
                                String[] line;
                                int lineNo = 0;
                                while ((line = reader.readNext()) != null)
                                {
                                    lineNo++;
                                    if (lineNo == 1)
                                    {
                                        continue; //header
                                    }

                                    Map<String, Object> map = new CaseInsensitiveHashMap<>();
                                    map.put("releaseId", guid);
                                    map.put("category", line[0]);
                                    map.put("metricName", line[1]);
                                    map.put("value", line[2]);
                                    map.put("objectId", new GUID().toString());
                                    releaseStatsRows.add(map);
                                }
                            }
                        }
                        else
                        {
                            job.getLogger().error("unable to find release stats file: " + releaseStats.getPath());
                        }
                    }
                    else
                    {
                        job.getLogger().info("This was selected as a test-only run, so skipping creation of release record");
                        job.getLogger().info("variant release record values:");
                        for (String field : row.keySet())
                        {
                            job.getLogger().info(field + ": " + row.get(field));
                        }
                    }
                }
                catch (Exception e)
                {
                    throw new PipelineJobException("Error parsing data: " + e.getMessage(), e);
                }
            }

            //finally do actual DB inserts, if successful
            if (!testOnly)
            {
                try (DbScope.Transaction transaction = DbScope.getLabKeyScope().ensureTransaction())
                {
                    job.getLogger().info("Publishing release to variant catalog table");
                    TableInfo variantReleaseTable = QueryService.get().getUserSchema(job.getUser(), job.getContainer(), mGAPSchema.NAME).getTable(mGAPSchema.TABLE_VARIANT_CATALOG_RELEASES);
                    BatchValidationException errors = new BatchValidationException();
                    variantReleaseTable.getUpdateService().insertRows(job.getUser(), job.getContainer(), variantReleaseRows, errors, null, new HashMap<>());
                    if (errors.hasErrors())
                    {
                        throw errors;
                    }

                    TableInfo variantTableInfo = QueryService.get().getUserSchema(job.getUser(), job.getContainer(), mGAPSchema.NAME).getTable(mGAPSchema.TABLE_VARIANT_TABLE);
                    BatchValidationException errors2 = new BatchValidationException();
                    variantTableInfo.getUpdateService().insertRows(job.getUser(), job.getContainer(), variantTableRows, errors2, null, new HashMap<>());
                    if (errors2.hasErrors())
                    {
                        throw errors2;
                    }

                    job.getLogger().info("total variant table records: " + variantTableRows.size());

                    TableInfo releaseStatsTable = QueryService.get().getUserSchema(job.getUser(), job.getContainer(), mGAPSchema.NAME).getTable(mGAPSchema.TABLE_RELEASE_STATS);
                    BatchValidationException errors3 = new BatchValidationException();
                    releaseStatsTable.getUpdateService().insertRows(job.getUser(), job.getContainer(), releaseStatsRows, errors3, null, new HashMap<>());
                    if (errors3.hasErrors())
                    {
                        throw errors3;
                    }

                    job.getLogger().info("total release stat records: " + releaseStatsRows.size());

                    transaction.commit();
                }
                catch (Exception e)
                {
                    throw new PipelineJobException("Error saving data: " + e.getMessage(), e);
                }
            }
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            //first build map of gene to geneName:
            ctx.getLogger().info("building map of gene symbol to name:");
            int gtfId = ctx.getParams().getInt("gtfFile");
            File gtf = ctx.getSequenceSupport().getCachedData(gtfId);
            if (gtf == null || !gtf.exists())
            {
                throw new PipelineJobException("Unable to find file: " + gtf);
            }

            GeneToNameTranslator translator = new GeneToNameTranslator(gtf, ctx.getLogger());

            for (SequenceOutputFile so : inputFiles)
            {
                ReferenceGenome genome = ctx.getSequenceSupport().getCachedGenome(so.getLibrary_id());

                RecordedAction action = new RecordedAction();
                action.addInput(so.getFile(), "Input VCF");
                action.addInput(new File(so.getFile().getPath() + ".tbi"), "Input VCF Index");

                boolean variantTableOnly = ctx.getParams().optBoolean("variantTableOnly", false);
                //if variantTableOnly is selected, automatically ignore all these.
                boolean removeAnnotations = !variantTableOnly && ctx.getParams().optBoolean("removeAnnotations", false);
                boolean sitesOnly = !variantTableOnly && ctx.getParams().optBoolean("sitesOnly", false);
                boolean snvOnly = !variantTableOnly && ctx.getParams().optBoolean("snvOnly", false);
                String releaseVersion = ctx.getParams().optString("releaseVersion", "0.0");

                File currentVCF = so.getFile();

                //count subjects
                int totalSubjects = 0;
                try (VCFFileReader reader = new VCFFileReader(so.getFile()))
                {
                    totalSubjects = reader.getFileHeader().getSampleNamesInOrder().size();
                }

                //remove removeAnnotations
                if (removeAnnotations)
                {
                    File outputFile = new File(ctx.getOutputDir(), SequenceAnalysisService.get().getUnzippedBaseName(currentVCF.getName()) + ".noAnnotations.vcf.gz");
                    if (indexExists(outputFile))
                    {
                        ctx.getLogger().info("re-using existing output: " + outputFile.getPath());
                    }
                    else
                    {
                        new AbstractGatkWrapper(ctx.getLogger())
                        {
                            public void execute(File input, File outputFile, File referenceFasta) throws PipelineJobException
                            {
                                List<String> args = new ArrayList<>();
                                args.add(SequencePipelineService.get().getJavaFilepath());
                                args.addAll(SequencePipelineService.get().getJavaOpts());
                                args.add("-jar");
                                File gatkJar = getJAR();
                                gatkJar = new File(getJAR().getParentFile(), FileUtil.getBaseName(gatkJar) + "-discvr.jar");
                                args.add(gatkJar.getPath());
                                args.add("-T");
                                args.add("RemoveAnnotations");
                                args.add("-R");
                                args.add(referenceFasta.getPath());
                                args.add("-V");
                                args.add(input.getPath());
                                args.add("-o");
                                args.add(outputFile.getPath());

                                for (String key : Arrays.asList("AF", "AC", "END", "ANN", "LOF", "MAF", "CADD_PH", "CADD_RS", "CCDS", "ENC", "ENCDNA_CT", "ENCDNA_SC", "ENCSEG_CT", "ENCSEG_NM", "ENCTFBS_CL", "ENCTFBS_SC", "ENCTFBS_TF", "ENN", "ERBCTA_CT", "ERBCTA_NM", "ERBCTA_SC", "ERBSEG_CT", "ERBSEG_NM", "ERBSEG_SC", "ERBSUM_NM", "ERBSUM_SC", "ERBTFBS_PB", "ERBTFBS_TF", "FC", "FE", "FS_EN", "FS_NS", "FS_SC", "FS_SN", "FS_TG", "FS_US", "FS_WS", "GRASP_AN", "GRASP_P", "GRASP_PH", "GRASP_PL", "GRASP_PMID", "GRASP_RS", "LOF", "NC", "NE", "NF", "NG", "NH", "NJ", "NK", "NL", "NM", "NMD", "OMIMC", "OMIMD", "OMIMM", "OMIMMUS", "OMIMN", "OMIMS", "OMIMT", "OREGANNO_PMID", "OREGANNO_TYPE", "PC_PL", "PC_PR", "PC_VB", "PP_PL", "PP_PR", "PP_VB", "RDB_MF", "RDB_WS", "RFG", "RSID", "SCSNV_ADA", "SCSNV_RS", "SD", "SF", "SM", "SP_SC", "SX", "TMAF", "LF", "CLN_ALLELE", "CLN_ALLELEID", "CLN_DN", "CLN_DNINCL", "CLN_DISDB", "CLN_DISDBINCL", "CLN_HGVS", "CLN_REVSTAT", "CLN_SIG", "CLN_SIGINCL", "CLN_VC", "CLN_VCSO", "CLN_VI", "CLN_DBVARID", "CLN_GENEINFO", "CLN_MC", "CLN_ORIGIN", "CLN_RS", "CLN_SSR"))
                                {
                                    args.add("-A");
                                    args.add(key);
                                }

                                //for (String key : Arrays.asList("DP", "AD"))
                                //{
                                //    args.add("-GA");
                                //    args.add(key);
                                //}

                                args.add("-ef");
                                args.add("--clearGenotypeFilter");
                                if (sitesOnly)
                                {
                                    args.add("--sites_only");
                                }

                                super.execute(args);
                            }
                        }.execute(currentVCF, outputFile, genome.getWorkingFastaFile());
                    }

                    currentVCF = outputFile;
                    ctx.getFileManager().addIntermediateFile(outputFile);
                    ctx.getFileManager().addIntermediateFile(new File(outputFile.getPath() + ".tbi"));
                }
                //NOTE: if removing annotations, this will be accomplished by the step above
                else if (sitesOnly)
                {
                    File outputFile = new File(ctx.getOutputDir(), SequenceAnalysisService.get().getUnzippedBaseName(currentVCF.getName()) + ".noGenotypes.vcf.gz");
                    if (indexExists(outputFile))
                    {
                        ctx.getLogger().info("re-using existing output: " + outputFile.getPath());
                    }
                    else
                    {
                        SelectVariantsWrapper wrapper = new SelectVariantsWrapper(ctx.getLogger());
                        wrapper.execute(genome.getWorkingFastaFile(), currentVCF, outputFile, Arrays.asList("--sites_only"));
                    }
                    currentVCF = outputFile;
                    ctx.getFileManager().addIntermediateFile(outputFile);
                    ctx.getFileManager().addIntermediateFile(new File(outputFile.getPath() + ".tbi"));
                }

                //SNPs only:
                if (snvOnly)
                {
                    File outputFile = new File(ctx.getOutputDir(), SequenceAnalysisService.get().getUnzippedBaseName(currentVCF.getName()) + ".snv.vcf.gz");
                    if (indexExists(outputFile))
                    {
                        ctx.getLogger().info("re-using existing output: " + outputFile.getPath());
                    }
                    else
                    {
                        SelectVariantsWrapper wrapper = new SelectVariantsWrapper(ctx.getLogger());
                        wrapper.execute(genome.getWorkingFastaFile(), currentVCF, outputFile, Arrays.asList("--selectTypeToInclude", "SNP"));
                    }
                    currentVCF = outputFile;
                    ctx.getFileManager().addIntermediateFile(outputFile);
                    ctx.getFileManager().addIntermediateFile(new File(outputFile.getPath() + ".tbi"));
                }

                if (!sitesOnly && !variantTableOnly)
                {
                    currentVCF = renameSamples(currentVCF, genome, ctx);
                    ctx.getFileManager().addIntermediateFile(currentVCF);
                    ctx.getFileManager().addIntermediateFile(new File(currentVCF.getPath() + ".tbi"));
                }

                //rename output
                File renamed = new File(ctx.getOutputDir(), "mGap.v" + FileUtil.makeLegalName(releaseVersion) + ".vcf.gz");
                try
                {
                    if (renamed.exists())
                    {
                        ctx.getLogger().info("deleting existing file: " + renamed.getPath());
                        renamed.delete();
                    }

                    File renamedIdx = new File(renamed.getPath() + ".tbi");
                    if (renamedIdx.exists())
                    {
                        ctx.getLogger().info("deleting existing file: " + renamedIdx.getPath());
                        renamedIdx.delete();
                    }

                    ctx.getLogger().info("Copying final vcf from: " + currentVCF.getPath());
                    ctx.getLogger().info("to: " + renamed.getPath());
                    FileUtils.copyFile(currentVCF, renamed);
                    FileUtils.copyFile(new File(currentVCF.getPath() + ".tbi"), renamedIdx);
                    currentVCF = renamed;
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                ctx.getLogger().info("inspecting VCF and creating summary table");
                long sitesInspected = 0L;

                long totalVariants = 0L;
                long totalPrivateVariants = 0L;
                File interestingVariantTable = new File(ctx.getOutputDir(), SequenceAnalysisService.get().getUnzippedBaseName(currentVCF.getName()) + ".variants.txt");
                try (VCFFileReader reader = new VCFFileReader(currentVCF); CloseableIterator<VariantContext> it = reader.iterator(); CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(interestingVariantTable), '\t', CSVWriter.NO_QUOTE_CHARACTER))
                {
                    writer.writeNext(new String[]{"Chromosome", "Position", "Reference", "Allele", "Source", "Reason", "Description", "Overlapping Gene(s)"});
                    while (it.hasNext())
                    {
                        Set<List<String>> queuedLines = new LinkedHashSet<>();

                        sitesInspected++;

                        if (sitesInspected % 1000000 == 0)
                        {
                            ctx.getLogger().info("inspected " + sitesInspected + " variants");
                        }

                        VariantContext vc = it.next();
                        if (vc.isFiltered())
                        {
                            continue;
                        }

                        totalVariants++;

                        //count private alleles.  note: this is counting alleles, not sites
                        for (Allele a : vc.getAlleles())
                        {
                            int sampleCount = 0;
                            for (Genotype g : vc.getGenotypes())
                            {
                                if (g.getAlleles().contains(a))
                                {
                                    sampleCount++;
                                    if (sampleCount > 1)
                                    {
                                        break;
                                    }
                                }
                            }

                            if (sampleCount == 1)
                            {
                                totalPrivateVariants++;
                            }
                        }

                        Set<String> overlappingGenes = new HashSet<>();
                        if (vc.getAttribute("ANN") != null)
                        {
                            List<String> anns = vc.getAttributeAsStringList("ANN", "");

                            //find overlapping genes first
                            for (String ann : anns)
                            {
                                if (StringUtils.isEmpty(ann))
                                {
                                    continue;
                                }

                                String[] tokens = ann.split("\\|");
                                if (tokens.length < 4)
                                {
                                    //intergenic modifiers
                                    continue;
                                }

                                if (!StringUtils.isEmpty(tokens[3]))
                                {
                                    String geneName = tokens[3];
                                    if (geneName.startsWith("ENSMMUE"))
                                    {
                                        //exons
                                        continue;
                                    }

                                    if (geneName.startsWith("gene:"))
                                    {
                                        geneName = geneName.replaceAll("gene:", "");
                                    }

                                    if (translator.getGeneMap().containsKey(geneName) && translator.getGeneMap().get(geneName).get("gene_name") != null)
                                    {
                                        geneName = translator.getGeneMap().get(geneName).get("gene_name");
                                    }

                                    overlappingGenes.add(geneName);
                                }
                            }

                            for (String ann : anns)
                            {
                                if (StringUtils.isEmpty(ann))
                                {
                                    continue;
                                }

                                String[] tokens = ann.split("\\|");

                                if ("HIGH".equals(tokens[2]))
                                {
                                    if (tokens.length < 10)
                                    {
                                        ctx.getLogger().error("unexpected ANN line at pos: " + vc.getContig() + " " + vc.getStart() + "[" + tokens + "]");
                                        continue;
                                    }

                                    String description = "Type: " + tokens[1] + ", Gene: " + tokens[3];
                                    if (tokens.length > 10 && !StringUtils.isEmpty(tokens[10]))
                                    {
                                        description += ", AA Change: " + tokens[10];
                                    }

                                    maybeWriteVariantLine(queuedLines, vc, tokens[0], "SNPEff", "Predicted High Impact", description, overlappingGenes);
                                }
                            }
                        }

                        if (vc.getAttribute("CLN_SIG") != null)
                        {
                            List<String> clnAlleles = vc.getAttributeAsStringList("CLN_ALLELE", "");
                            List<String> clnSigs = vc.getAttributeAsStringList("CLN_SIG", "");
                            List<String> clnDisease = vc.getAttributeAsStringList("CLN_DN", "");
                            int i = -1;
                            for (String sigList : clnSigs)
                            {
                                i++;

                                List<String> sigSplit = Arrays.asList(sigList.split("\\|"));
                                List<String> diseaseSplit = Arrays.asList(clnDisease.get(i).split("\\|"));
                                int j = 0;
                                for (String sig : sigSplit)
                                {
                                    //TODO: consider disease = not_provided
                                    if (isAllowableClinVarSig(sig))
                                    {
                                        String description = StringUtils.join(new String[]{
                                                "Significance: " + sig
                                        }, ",");

                                        String allele = clnAlleles.get(i);
                                        maybeWriteVariantLine(queuedLines, vc, allele, "ClinVar", diseaseSplit.get(j), description, overlappingGenes);
                                    }

                                    j++;
                                }
                            }
                        }

                        //NE: nsdb Polyphen2_HVAR_score: Polyphen2 score based on HumVar, i.e. hvar_prob. The score ranges from 0 to 1, and the corresponding prediction is 'probably damaging' if it is in [0.909,1], 'possibly damaging' if it is in [0.447,0.908], 'benign' if it is in [0,0.446]. Score cutoff for binary classification is 0.5, i.e. the prediction is 'neutral' if the score is smaller than 0.5 and 'deleterious' if the score is larger than 0.5. Multiple entries separated by
                        //NF: nsdb Polyphen2_HVAR_pred: Polyphen2 prediction based on HumVar, 'D' ('probably damaging'),'P' ('possibly damaging') and 'B' ('benign'). Multiple entries separated by
                        if (vc.getAttribute("NF") != null && !".".equals(vc.getAttribute("NF")))
                        {
                            Set<String> polyphenPredictions = new HashSet<>(vc.getAttributeAsStringList("NF", null));
                            polyphenPredictions.remove("B");
                            polyphenPredictions.remove("P");

                            if (!polyphenPredictions.isEmpty())
                            {
                                Double maxScore = Collections.max(vc.getAttributeAsDoubleList("NE", 0.0));
                                String description = StringUtils.join(new String[]{
                                        "Score: " + String.valueOf(maxScore)
                                }, ",");

                                maybeWriteVariantLine(queuedLines, vc, null, "Polyphen2", "Prediction: " + StringUtils.join(polyphenPredictions, ","), description, overlappingGenes);
                            }
                        }

                        for (List<String> line : queuedLines)
                        {
                            writer.writeNext(line.toArray(new String[line.size()]));
                        }
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                boolean testOnly = ctx.getParams().optBoolean("testOnly", false);

                generateSummaries(ctx, currentVCF, genome, totalVariants, totalPrivateVariants, totalSubjects);

                ctx.getFileManager().removeIntermediateFile(currentVCF);
                ctx.getFileManager().removeIntermediateFile(new File(currentVCF.getPath(), ".tbi"));

                if (!variantTableOnly)
                {
                    SequenceOutputFile output = new SequenceOutputFile();
                    output.setFile(currentVCF);
                    output.setName("mGAP Release: " + releaseVersion);
                    output.setCategory((testOnly ? "Test " : "") + "mGAP Release");
                    output.setLibrary_id(genome.getGenomeId());
                    ctx.getFileManager().addSequenceOutput(output);

                    SequenceOutputFile output2 = new SequenceOutputFile();
                    output2.setFile(interestingVariantTable);
                    output2.setName("mGAP Release: " + releaseVersion + " Variant Table");
                    output2.setCategory((testOnly ? "Test " : "") + "mGAP Release Variant Table");
                    output2.setLibrary_id(genome.getGenomeId());
                    ctx.getFileManager().addSequenceOutput(output2);
                }
            }
        }

        private boolean isAllowableClinVarSig(String x)
        {
            return !(StringUtils.isEmpty(x) || x.toLowerCase().contains("benign") || x.toLowerCase().contains("unknown") || x.toLowerCase().contains("uncertain") || x.contains("not_specified") || x.contains("not_provided"));
        }

        private void generateSummaries(JobContext ctx, File vcf, ReferenceGenome genome, long totalVariants, long totalPrivateVariants, int totalSubjects) throws PipelineJobException
        {
            //variants to table
            ctx.getLogger().info("Running VariantsToTable");
            File variantsToTable = new File(ctx.getOutputDir(), SequenceAnalysisService.get().getUnzippedBaseName(vcf.getName()) + ".variantsToTable.txt");
            File tableCheck = new File(variantsToTable.getPath() + ".done");
            if (!tableCheck.exists())
            {
                VariantsToTableRunner vtt = new VariantsToTableRunner(ctx.getLogger());
                List<String> fields = new ArrayList<>(Arrays.asList("POS", "REF", "ALT", "FILTER"));
                fields.addAll(allowedFields);
                vtt.execute(vcf, variantsToTable, genome.getWorkingFastaFile(), fields);

                try
                {
                    FileUtils.touch(tableCheck);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }
            else
            {
                ctx.getLogger().info("resuming with existing file: " + variantsToTable.getPath());
            }

            ctx.getFileManager().addIntermediateFile(variantsToTable);
            ctx.getFileManager().addIntermediateFile(tableCheck);

            //generate stats
            ctx.getLogger().info("Generating summary stats from: " + variantsToTable.getName());
            File summaryTable = new File(vcf.getParentFile(), SequenceAnalysisService.get().getUnzippedBaseName(vcf.getName()) + ".summary.txt");
            File summaryTableByField = new File(vcf.getParentFile(), SequenceAnalysisService.get().getUnzippedBaseName(vcf.getName()) + ".summaryByField.txt");
            generateSummary(ctx, variantsToTable, summaryTable, summaryTableByField, totalVariants, totalPrivateVariants, totalSubjects);
        }

        private static final List<String> allowedFields = Arrays.asList("CHROM", "ANN", "CLN_SIG", "GRASP_PH", "ENCTFBS_TF", "FE", "NF", "ENCSEG_NM");

        private class FieldTracker
        {
            private Map<String, FieldData> perField;

            public FieldTracker(int size)
            {
                perField = new HashMap<>(size);
            }

            public void add(String fieldName, String val)
            {
                if (!allowedFields.contains(fieldName))
                {
                    return;
                }

                if (!StringUtils.isEmpty(val) && !"NA".equals(val))
                {
                    if ("ANN".equals(fieldName))
                    {
                        boolean isHighImpact = false;
                        Set<String> codingPotential = new HashSet<>();
                        String[] tokens = val.split(",");
                        for (String v : tokens)
                        {
                            String[] split = v.split("\\|");
                            if ("HIGH".equals(split[2]))
                            {
                                isHighImpact = true;
                            }

                            String[] types = split[1].split("&");
                            codingPotential.addAll(Arrays.asList(types));
                        }

                        if (isHighImpact)
                        {
                            addForValue("AnnotationSummary", "Predicted High Impact (SnpEff)");
                        }

                        //coding potential:
                        for (String type : codingPotential)
                        {
                            addForValue("CodingPotential", type);
                        }
                    }
                    else if ("CLN_SIG".equals(fieldName))
                    {
                        boolean hasOverlap = false;
                        boolean hasAnyOverlap = false;
                        String[] tokens = val.split(",");
                        for (String v : tokens)
                        {
                            if (StringUtils.isEmpty(v))
                            {
                                continue;
                            }

                            hasAnyOverlap = true;

                            String[] split = v.split("\\|");
                            for (String sig : split)
                            {
                                //TODO: consider disease = not_provided
                                if (isAllowableClinVarSig(sig))
                                {
                                    hasOverlap = true;
                                    break;
                                }
                            }
                        }

                        if (hasOverlap)
                        {
                            addForValue("AnnotationSummary", "ClinVar Overlap (Pathogenic)");
                        }

                        if (hasAnyOverlap)
                        {
                            addForValue("AnnotationSummary", "ClinVar Overlap");
                        }
                    }
                    else if ("GRASP_PH".equals(fieldName))
                    {
                        addForValue("AnnotationSummary", "GWAS Associations (GRASP)");
                    }
                    else if ("FE".equals(fieldName) && "Y".equals(val))
                    {
                        addForValue("AnnotationSummary", "Enhancer Region (FANTOM5)");
                    }
                    else if ("ENCTFBS_TF".equals(fieldName))
                    {
                        addForValue("AnnotationSummary", "Transcription Factor Binding (ENCODE)");
                    }
                    else if ("NF".equals(fieldName) && val.contains("D"))
                    {
                        addForValue("AnnotationSummary", "Damaging (Polyphen2)");
                    }
                    else if ("ENCSEG_NM".equals(fieldName))
                    {
                        List<String> values = Arrays.asList(val.split(","));
                        if (values.contains("E"))
                        {
                            addForValue("AnnotationSummary", "Predicted Enhancer (ENCODE)");
                        }
                    }
                    else if ("CHROM".equals(fieldName))
                    {
                        addForValue("PerChromosome", val);
                    }
                }
            }

            private void addForValue(String fieldName, String val)
            {
                if (allowForLevel(val))
                {
                    FieldData data = perField.get(fieldName);
                    if (data == null)
                    {
                        data = new FieldData();
                    }

                    Integer count = data.countsPerLevel.get(val);
                    if (count == null)
                    {
                        count = 0;
                    }
                    count++;

                    data.countsPerLevel.put(val, count);

                    perField.put(fieldName, data);
                }
            }

            private boolean allowForLevel(String val)
            {
                //if not numeric, accept it
                if (!NumberUtils.isCreatable(val))
                {
                    return true;
                }

                //otherwise allow only integers
                return NumberUtils.isDigits(val);
            }
        }

        private class FieldData
        {
            int nonNull = 0;
            Map<String, Integer> countsPerLevel = new HashMap<>(20);
        }

        private void generateSummary(JobContext ctx, File variantsToTable, File output, File outputPerValue, long totalVariants, long totalPrivateVariants, int totalSubjects) throws PipelineJobException
        {
            ctx.getLogger().info("reading variant table");
            int lineNo = 0;
            FieldTracker tracker = new FieldTracker(130);
            try (BufferedReader reader = Readers.getReader(variantsToTable))
            {
                lineNo++;
                if (lineNo % 1000000 == 0)
                {
                    ctx.getLogger().info("processed " + lineNo + " lines");
                }

                String lineStr;
                List<String> header = new ArrayList<>();
                int lineCount = 0;
                while ((lineStr = reader.readLine()) != null)
                {
                    String[] line = lineStr.split("\t");
                    lineCount++;
                    if (lineCount == 1)
                    {
                        //skip header
                        header = Arrays.asList(line);
                        continue;
                    }

                    //skip basic site information, but do include CHROM, since that might be useful to see summarized
                    for (int i = 4; i < line.length; i++)
                    {
                        tracker.add(header.get(i), line[i]);
                    }
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            ctx.getLogger().info("writing summary tables");
            try (CSVWriter writer = new CSVWriter(IOUtil.openFileForBufferedWriting(output), '\t', CSVWriter.NO_QUOTE_CHARACTER); CSVWriter valWriter = new CSVWriter(IOUtil.openFileForBufferedWriting(outputPerValue), '\t', CSVWriter.NO_QUOTE_CHARACTER))
            {
                writer.writeNext(new String[]{"Field", "Category", "NonNull", "TotalDistinct", "Levels"});
                for (String fn : new TreeSet<>(tracker.perField.keySet()))
                {
                    FieldData data = tracker.perField.get(fn);

                    String vals = "";
                    if (data.countsPerLevel.size() < 10)
                    {
                        vals = StringUtils.join(data.countsPerLevel.keySet(), ",");
                    }

                    writer.writeNext(new String[]{fn, "None", String.valueOf(data.nonNull), (data.countsPerLevel.isEmpty() ? "" : String.valueOf(data.countsPerLevel.size())), vals});
                }

                valWriter.writeNext(new String[]{"Field", "Level", "Total"});
                valWriter.writeNext(new String[]{"Counts", "TotalVariants", String.valueOf(totalVariants)});
                valWriter.writeNext(new String[]{"Counts", "TotalPrivateVariants", String.valueOf(totalPrivateVariants)});
                valWriter.writeNext(new String[]{"Counts", "TotalSamples", String.valueOf(totalSubjects)});
                for (String fn : new TreeSet<>(tracker.perField.keySet()))
                {
                    FieldData data = tracker.perField.get(fn);
                    for (String val : data.countsPerLevel.keySet())
                    {
                        valWriter.writeNext(new String[]{fn, val, String.valueOf(data.countsPerLevel.get(val))});
                    }
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        private void maybeWriteVariantLine(Set<List<String>> queuedLines, VariantContext vc, @Nullable String allele, String source, String reason, String description, Collection<String> overlappingGenes)
        {
            if (allele == null)
            {
                List<String> alts = new ArrayList<>();
                vc.getAlternateAlleles().forEach(a -> alts.add(a.getDisplayString()));
                allele = StringUtils.join(alts, ",");
            }

            queuedLines.add(Arrays.asList(vc.getContig(), String.valueOf(vc.getStart()), vc.getReference().getDisplayString(), allele, source, reason, description, StringUtils.join(overlappingGenes, ";")));
        }

        private Map<String, String> parseSampleMap(File sampleMapFile) throws PipelineJobException
        {
            Map<String, String> ret = new HashMap<>();
            try (CSVReader reader = new CSVReader(Readers.getReader(sampleMapFile), '\t'))
            {
                String[] line;
                while ((line = reader.readNext()) != null)
                {
                    ret.put(line[0], line[1]);
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            return ret;
        }

        private File renameSamples(File currentVCF, ReferenceGenome genome, JobContext ctx) throws PipelineJobException
        {
            ctx.getLogger().info("renaming samples in VCF");
            File outputFile = new File(currentVCF.getParentFile(), SequenceAnalysisService.get().getUnzippedBaseName(currentVCF.getName()) + ".renamed.vcf.gz");
            if (indexExists(outputFile))
            {
                ctx.getLogger().info("re-using existing output: " + outputFile.getPath());
            }
            else
            {
                Map<String, String> sampleMap = parseSampleMap(getSampleNameFile(ctx.getSourceDirectory()));

                VariantContextWriterBuilder builder = new VariantContextWriterBuilder();
                builder.setReferenceDictionary(SAMSequenceDictionaryExtractor.extractDictionary(genome.getSequenceDictionary()));
                builder.setOutputFile(outputFile);
                builder.setOption(Options.USE_ASYNC_IO);

                try (VCFFileReader reader = new VCFFileReader(currentVCF); VariantContextWriter writer = builder.build())
                {
                    VCFHeader header = reader.getFileHeader();
                    List<String> samples = header.getSampleNamesInOrder();
                    List<String> remappedSamples = new ArrayList<>();

                    for (String sample : samples)
                    {
                        if (sampleMap.containsKey(sample))
                        {
                            remappedSamples.add(sampleMap.get(sample));
                        }
                        else
                        {
                            throw new PipelineJobException("No alternate name provided for sample: " + sample);
                        }
                    }

                    if (remappedSamples.size() != samples.size())
                    {
                        throw new PipelineJobException("The number of renamed samples does not equal starting samples: " + samples.size() + " / " + remappedSamples.size());
                    }

                    writer.writeHeader(new VCFHeader(header.getMetaDataInInputOrder(), remappedSamples));
                    try (CloseableIterator<VariantContext> it = reader.iterator())
                    {
                        while (it.hasNext())
                        {
                            writer.add(it.next());
                        }
                    }
                }
            }

            return outputFile;
        }

        private boolean indexExists(File vcf)
        {
            File idx = new File(vcf.getPath() + ".tbi");
            return idx.exists();
        }
    }
}
