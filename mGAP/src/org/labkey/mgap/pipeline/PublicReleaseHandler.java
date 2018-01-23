package org.labkey.mgap.pipeline;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.Results;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.StopIteratingException;
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
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractGatkWrapper;
import org.labkey.api.sequenceanalysis.run.SelectVariantsWrapper;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.mgap.mGAPModule;
import org.labkey.mgap.mGAPSchema;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 5/2/2017.
 */
public class PublicReleaseHandler extends AbstractParameterizedOutputHandler
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
                    put("checked", true);
                }}, true),
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
    public OutputProcessor getProcessor()
    {
        return new Processor();
    }

    public static class Processor implements OutputProcessor
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
                try (VCFFileReader reader = new VCFFileReader(so.getFile()))
                {
                    VCFHeader header = reader.getFileHeader();
                    TableInfo ti = QueryService.get().getUserSchema(job.getUser(), (job.getContainer().isWorkbook() ? job.getContainer().getParent() : job.getContainer()), mGAPSchema.NAME).getTable(mGAPSchema.TABLE_ANIMAL_MAPPING);
                    TableSelector ts = new TableSelector(ti, PageFlowUtil.set("subjectname", "externalAlias"), new SimpleFilter(FieldKey.fromString("subjectname"), header.getSampleNamesInOrder(), CompareType.IN), null);
                    ts.forEachResults(new Selector.ForEachBlock<Results>()
                    {
                        @Override
                        public void exec(Results rs) throws SQLException, StopIteratingException
                        {
                            sampleNameMap.put(rs.getString(FieldKey.fromString("subjectname")), rs.getString(FieldKey.fromString("externalAlias")));
                        }
                    });
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

            for (SequenceOutputFile so : outputsCreated)
            {
                if (so.getRowid() == null || so.getRowid() == 0)
                {
                    throw new PipelineJobException("No rowId found for sequence output");
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
                            if (totalVariants % 100000 == 0)
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
                        job.getLogger().info("attempting to infer total subject from original VCF");
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
                row.put("genomeId", so.getLibrary_id());
                row.put("totalSubjects", totalSubjects);
                row.put("totalVariants", totalVariants);

                try
                {
                    boolean testOnly = ConvertHelper.convert(job.getParameters().get("testOnly"), boolean.class);
                    if (!testOnly){
                        job.getLogger().info("Publishing release to variant catalog table");
                        TableInfo variants = QueryService.get().getUserSchema(job.getUser(), job.getContainer(), mGAPSchema.NAME).getTable(mGAPSchema.TABLE_VARIANT_CATALOG_RELEASES);
                        BatchValidationException errors = new BatchValidationException();
                        variants.getUpdateService().insertRows(job.getUser(), job.getContainer(), Arrays.asList(row), errors, null, new HashMap<>());

                        if (errors.hasErrors())
                        {
                            throw errors;
                        }
                    }
                    else
                    {
                        job.getLogger().info("This was selected as a test-only run, so skipping creation of release record");
                    }
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
            for (SequenceOutputFile so : inputFiles)
            {
                ReferenceGenome genome = ctx.getSequenceSupport().getCachedGenome(so.getLibrary_id());

                RecordedAction action = new RecordedAction();
                action.addInput(so.getFile(), "Input VCF");
                action.addInput(new File(so.getFile().getPath() + ".tbi"), "Input VCF Index");

                boolean removeAnnotations = ctx.getParams().optBoolean("removeAnnotations", false);
                boolean sitesOnly = ctx.getParams().optBoolean("sitesOnly", false);
                boolean snvOnly = ctx.getParams().optBoolean("snvOnly", false);
                String releaseVersion = ctx.getParams().optString("releaseVersion", "0.0");

                File currentVCF = so.getFile();

                //remove removeAnnotations
                if (removeAnnotations)
                {
                    File outputFile = new File(ctx.getOutputDir(), SequenceAnalysisService.get().getUnzippedBaseName(currentVCF.getName()) + ".noAnnotations.vcf.gz");
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

                            for (String key : Arrays.asList("END", "ANN", "LOF", "MAF", "CADD_PH", "CADD_RS", "CCDS", "ENC", "ENCDNA_CT", "ENCDNA_SC", "ENCSEG_CT", "ENCSEG_NM", "ENCTFBS_CL", "ENCTFBS_SC", "ENCTFBS_TF", "ENN", "ERBCTA_CT", "ERBCTA_NM", "ERBCTA_SC", "ERBSEG_CT", "ERBSEG_NM", "ERBSEG_SC", "ERBSUM_NM", "ERBSUM_SC", "ERBTFBS_PB", "ERBTFBS_TF", "FC", "FE", "FS_EN", "FS_NS", "FS_SC", "FS_SN", "FS_TG", "FS_US", "FS_WS", "GRASP_AN", "GRASP_P", "GRASP_PH", "GRASP_PL", "GRASP_PMID", "GRASP_RS", "LOF", "NC", "NE", "NF", "NG", "NH", "NJ", "NK", "NL", "NM", "NMD", "OMIMC", "OMIMD", "OMIMM", "OMIMMUS", "OMIMN", "OMIMS", "OMIMT", "OREGANNO_PMID", "OREGANNO_TYPE", "PC_PL", "PC_PR", "PC_VB", "PP_PL", "PP_PR", "PP_VB", "RDB_MF", "RDB_WS", "RFG", "RSID", "SCSNV_ADA", "SCSNV_RS", "SD", "SF", "SM", "SP_SC", "SX", "TMAF", "LF", "CLN_ALLELE", "CLN_ALLELEID", "CLN_DN", "CLN_DNINCL", "CLN_DISDB", "CLN_DISDBINCL", "CLN_HGVS", "CLN_REVSTAT", "CLN_SIG", "CLN_SIGINCL", "CLN_VC", "CLN_VCSO", "CLN_VI", "CLN_DBVARID", "CLN_GENEINFO", "CLN_MC", "CLN_ORIGIN", "CLN_RS", "CLN_SSR"))
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
                                args.add("-sitesOnly");
                            }

                            super.execute(args);
                        }
                    }.execute(currentVCF, outputFile, genome.getWorkingFastaFile());

                    currentVCF = outputFile;
                    ctx.getFileManager().addIntermediateFile(outputFile);
                    ctx.getFileManager().addIntermediateFile(new File(outputFile.getPath() + ".tbi"));
                }
                else if (sitesOnly)
                {
                    File outputFile = new File(ctx.getOutputDir(), SequenceAnalysisService.get().getUnzippedBaseName(currentVCF.getName()) + ".noGenotypes.vcf.gz");
                    SelectVariantsWrapper wrapper = new SelectVariantsWrapper(ctx.getLogger());
                    wrapper.execute(genome.getWorkingFastaFile(), currentVCF, outputFile, Arrays.asList("-sitesOnly"));
                    currentVCF = outputFile;
                    ctx.getFileManager().addIntermediateFile(outputFile);
                    ctx.getFileManager().addIntermediateFile(new File(outputFile.getPath() + ".tbi"));
                }

                //SNPs only:
                if (snvOnly)
                {
                    File outputFile = new File(ctx.getOutputDir(), SequenceAnalysisService.get().getUnzippedBaseName(currentVCF.getName()) + ".snv.vcf.gz");
                    SelectVariantsWrapper wrapper = new SelectVariantsWrapper(ctx.getLogger());
                    wrapper.execute(genome.getWorkingFastaFile(), currentVCF, outputFile, Arrays.asList("--selectTypeToInclude", "SNP"));
                    currentVCF = outputFile;
                    ctx.getFileManager().addIntermediateFile(outputFile);
                    ctx.getFileManager().addIntermediateFile(new File(outputFile.getPath() + ".tbi"));
                }

                if (!sitesOnly)
                {
                    currentVCF = renameSamples(currentVCF, genome, ctx);
                }

                //rename output
                File renamed = new File(ctx.getOutputDir(), "mGap.v" + FileUtil.makeLegalName(releaseVersion) + ".vcf.gz");
                try
                {
                    ctx.getLogger().info("Moving final vcf from: " + currentVCF.getPath());
                    ctx.getLogger().info("to: " + renamed.getPath());
                    FileUtils.moveFile(currentVCF, renamed);
                    FileUtils.moveFile(new File(currentVCF.getPath() + ".tbi"), new File(renamed.getPath() + ".tbi"));
                    currentVCF = renamed;
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                //TODO: create VCFs/tables with:
                // high impact only
                // clinvar only
//                try (VCFFileReader reader = new VCFFileReader(currentVCF);CloseableIterator<VariantContext> it = reader.iterator())
//                {
//                    while (it.hasNext())
//                    {
//                        VariantContext vc = it.next();
//                        if (vc.isFiltered())
//                        {
//                            continue;
//                        }
//
//                        if (vc.getAttribute("ANN") != null)
//                        {
//                            List<String> anns = vc.getAttributeAsStringList("ANN", "");
//                            for (String ann : anns)
//                            {
//
//                            }
//                        }
//
//                        if (vc.getAttribute("CLN_ALLELE") != null)
//                        {
//                            List<String> clns = vc.getAttributeAsStringList("CLN_ALLELE", "");
//                            for (String cln : clns)
//                            {
//
//                            }
//                        }
//                    }
//                }

                boolean testOnly = ctx.getParams().optBoolean("testOnly", false);

                SequenceOutputFile output = new SequenceOutputFile();
                output.setFile(currentVCF);
                output.setName("mGAP Release: " + releaseVersion);
                output.setCategory((testOnly ? "Test " : "") + "mGAP Release");
                output.setLibrary_id(genome.getGenomeId());
                ctx.getFileManager().addSequenceOutput(output);
            }
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

            return outputFile;
        }
    }
}
