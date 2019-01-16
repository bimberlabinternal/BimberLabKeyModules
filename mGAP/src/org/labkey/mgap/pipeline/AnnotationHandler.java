package org.labkey.mgap.pipeline;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFFileReader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.spi.LoggerRepository;
import org.json.JSONObject;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.FieldKey;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.TaskFileManager;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.SelectVariantsWrapper;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileType;
import org.labkey.api.util.Job;
import org.labkey.api.util.JobRunner;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SafeFileAppender;
import org.labkey.api.writer.PrintWriters;
import org.labkey.mgap.mGAPModule;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by bimber on 5/2/2017.
 */
public class AnnotationHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private final FileType _vcfType = new FileType(Arrays.asList(".vcf"), ".vcf", false, FileType.gzSupportLevel.SUPPORT_GZ);
    private static final String GRCH37 = "genome37";
    private static final String CLINVAR_VCF = "clinvar37";
    private static final String CHAIN_FILE = "CHAIN_FILE";

    public AnnotationHandler()
    {
        super(ModuleLoader.getInstance().getModule(mGAPModule.class), "Annotate VCF for mGAP", "This will annotate an input NHP VCF using human ClinVar and Cassandra annotations.  This jobs will automatically look for chain files based on the source VCF genome and GRCh37/38 targets and will fail if these are not found.", new LinkedHashSet<String>(Arrays.asList("ldk/field/ExpDataField.js")), Arrays.asList(
                ToolParameterDescriptor.createExpDataParam(CLINVAR_VCF, "Clinvar 2.0 VCF (GRCh37)", "If selected, only variants of the type SNV will be included.", "ldk-expdatafield", new JSONObject()
                {{
                    put("allowBlank", false);
                }}, null),
                ToolParameterDescriptor.create(GRCH37, "GRCh37 Genome", "The genome that matches human GRCh37.", "ldk-simplelabkeycombo", new JSONObject()
                {{
                    put("width", 400);
                    put("schemaName", "sequenceanalysis");
                    put("queryName", "reference_libraries");
                    put("containerPath", "js:Laboratory.Utils.getQueryContainerPath()");
                    put("filterArray", "js:[LABKEY.Filter.create('datedisabled', null, LABKEY.Filter.Types.ISBLANK)]");
                    put("displayField", "name");
                    put("valueField", "rowid");
                    put("allowBlank", false);
                }}, null),
                ToolParameterDescriptor.create("dropFiltered", "Drop Filtered Sites", "If checked, filtered sites will be discarded, which can substantially improve speed.", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, true)
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

    public class Processor  implements SequenceOutputProcessor
    {
        public Processor()
        {

        }

        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            Set<Integer> genomeIds = new HashSet<>();
            for (SequenceOutputFile so : inputFiles)
            {
                genomeIds.add(so.getLibrary_id());
            }

            if (genomeIds.size() != 1)
            {
                throw new PipelineJobException("All inputs must be from the same genome");
            }

            int sourceGenome = genomeIds.iterator().next();

            //cache references:
            support.cacheGenome(SequenceAnalysisService.get().getReferenceGenome(sourceGenome, job.getUser()));
            support.cacheGenome(SequenceAnalysisService.get().getReferenceGenome(params.getInt(GRCH37), job.getUser()));

            //find chain files:
            findChainFile(sourceGenome, params.getInt(GRCH37), support, job);
        }

        private void findChainFile(int sourceGenome, int targetGenome, SequenceAnalysisJobSupport support, PipelineJob job) throws PipelineJobException
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("genomeId1"), sourceGenome);
            filter.addCondition(FieldKey.fromString("genomeId2"), targetGenome);
            filter.addCondition(FieldKey.fromString("dateDisabled"), null, CompareType.ISBLANK);

            TableSelector ts = new TableSelector(DbSchema.get("sequenceanalysis", DbSchemaType.Module).getTable("chain_files"), PageFlowUtil.set("chainFile"), filter, new Sort("-version"));
            if (!ts.exists())
            {
                throw new PipelineJobException("Unable to find chain file from genome " + sourceGenome + " to " + targetGenome);
            }

            if (ts.getRowCount() > 1)
            {
                job.getLogger().warn("more than one active chain file found from genome " + sourceGenome + " to " + targetGenome);
            }

            Integer chainId = ts.getObject(Integer.class);
            ExpData data = ExperimentService.get().getExpData(chainId);
            if (data == null)
            {
                throw new PipelineJobException("Unable to find ExpData chain file from genome " + sourceGenome + " to " + targetGenome + " with id: " + chainId);
            }

            support.cacheExpData(data);
            support.cacheObject(CHAIN_FILE, chainId);
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            File clinvarVCF = ctx.getSequenceSupport().getCachedData(ctx.getParams().getInt(CLINVAR_VCF));
            ReferenceGenome grch37Genome = ctx.getSequenceSupport().getCachedGenome(ctx.getParams().getInt(GRCH37));
            Integer chainFileId = ctx.getSequenceSupport().getCachedObject(CHAIN_FILE, Integer.class);
            File chainFile = ctx.getSequenceSupport().getCachedData(chainFileId);

            for (SequenceOutputFile so : inputFiles)
            {
                ctx.getLogger().info("processing file: " + so.getName());

                ReferenceGenome originalGenome = ctx.getSequenceSupport().getCachedGenome(so.getLibrary_id());

                RecordedAction action = new RecordedAction(getName());
                ctx.getFileManager().addInput(action, "Input VCF", so.getFile());
                ctx.getFileManager().addInput(action, "Input VCF Index", new File(so.getFile().getPath() + ".tbi"));

                //drop genotypes so all subsequent steps are faster
                int totalSubjects;
                String firstSample;
                try (VCFFileReader reader = new VCFFileReader(so.getFile()))
                {
                    totalSubjects = reader.getFileHeader().getSampleNamesInOrder().size();
                    firstSample = reader.getFileHeader().getSampleNamesInOrder().get(0);
                }

                boolean dropGenotypes = totalSubjects > 10;
                boolean dropFiltered = ctx.getParams().optBoolean("dropFiltered", true);

                File currentVcf = so.getFile();
                if (dropGenotypes || dropFiltered)
                {
                    if (dropGenotypes)
                        ctx.getLogger().info("dropping most genotypes prior to liftover for performance reasons.  a single is retained since cassandra requires one.");
                    if (dropFiltered)
                        ctx.getLogger().info("dropping filtered sites");

                    File subset = new File(ctx.getOutputDir(), SequenceAnalysisService.get().getUnzippedBaseName(so.getFile().getName()) + ".subset.vcf.gz");
                    if (!indexExists(subset))
                    {
                        List<String> selectArgs = new ArrayList<>();
                        if (dropGenotypes)
                        {
                            selectArgs.add("-sn");
                            selectArgs.add(firstSample);
                        }

                        if (dropFiltered)
                        {
                            selectArgs.add("-ef");
                        }

                        SelectVariantsWrapper wrapper = new SelectVariantsWrapper(ctx.getLogger());
                        wrapper.execute(originalGenome.getWorkingFastaFile(), so.getFile(), subset, selectArgs);
                    }
                    else
                    {
                        ctx.getLogger().info("resuming with existing file: " + subset.getPath());
                    }
                    ctx.getFileManager().addOutput(action, "VCF Subset", subset);
                    ctx.getFileManager().addIntermediateFile(subset);
                    ctx.getFileManager().addIntermediateFile(new File(subset.getPath() + ".tbi"));

                    currentVcf = subset;

                    ctx.getJob().getLogger().info("total variants: " + SequenceAnalysisService.get().getVCFLineCount(currentVcf, ctx.getJob().getLogger(), false));
                    ctx.getJob().getLogger().info("passing variants: " + SequenceAnalysisService.get().getVCFLineCount(currentVcf, ctx.getJob().getLogger(), true));
                }
                else
                {
                    ctx.getLogger().info("no subsetting of genotypes or filtered sites necessary");
                }

                //lift to target genome
                ctx.getLogger().info("lift to genome: " + grch37Genome.getGenomeId());

                File liftedToGRCh37 = new File(ctx.getOutputDir(), SequenceAnalysisService.get().getUnzippedBaseName(currentVcf.getName()) + ".liftTo" + grch37Genome.getGenomeId() + ".vcf.gz");
                File liftoverRejects = new File(ctx.getOutputDir(), SequenceAnalysisService.get().getUnzippedBaseName(currentVcf.getName()) + ".liftoverReject" + grch37Genome.getGenomeId() + ".vcf.gz");
                if (!indexExists(liftoverRejects))
                {
                    LiftoverVcfRunner liftoverVcfRunner = new LiftoverVcfRunner(ctx.getLogger());
                    liftoverVcfRunner.doLiftover(currentVcf, chainFile, grch37Genome.getWorkingFastaFile(), liftoverRejects, liftedToGRCh37, 0.95);
                }
                else
                {
                    ctx.getLogger().info("resuming with existing file: " + liftedToGRCh37.getPath());
                }
                ctx.getFileManager().addOutput(action, "VCF Lifted to GRCh37", liftedToGRCh37);
                ctx.getFileManager().addIntermediateFile(liftedToGRCh37);
                ctx.getFileManager().addIntermediateFile(new File(liftedToGRCh37.getPath() + ".tbi"));

                //annotate with clinvar
                ctx.getLogger().info("annotating with ClinVar 2.0");
                File clinvarAnnotated = new File(ctx.getOutputDir(), SequenceAnalysisService.get().getUnzippedBaseName(liftedToGRCh37.getName()) + ".cv.vcf.gz");
                if (!indexExists(clinvarAnnotated))
                {
                    ClinvarAnnotatorRunner cvRunner = new ClinvarAnnotatorRunner(ctx.getLogger());
                    cvRunner.execute(liftedToGRCh37, clinvarVCF, clinvarAnnotated);
                }
                else
                {
                    ctx.getLogger().info("resuming with existing file: " + clinvarAnnotated.getPath());
                }
                ctx.getFileManager().addOutput(action, "VCF Annotated With ClinVar2.0", clinvarAnnotated);
                ctx.getFileManager().addIntermediateFile(clinvarAnnotated);
                ctx.getFileManager().addIntermediateFile(new File(clinvarAnnotated.getPath() + ".tbi"));


                //annotate with cassandra
                ctx.getLogger().info("annotating with Cassandra");
                File cassandraAnnotated = new File(ctx.getOutputDir(), SequenceAnalysisService.get().getUnzippedBaseName(liftedToGRCh37.getName()) + ".cassandra.vcf.gz");
                if (!indexExists(cassandraAnnotated))
                {
                    List<File> files = runCassandraPerChromosome(ctx, liftedToGRCh37, cassandraAnnotated, grch37Genome);

                    ctx.getLogger().info("combining cassandra VCFs: ");
                    File outputUnzip = new File(ctx.getOutputDir(), SequenceAnalysisService.get().getUnzippedBaseName(liftedToGRCh37.getName()) + ".cassandra.vcf");
                    List<String> bashCommands = new ArrayList<>();
                    int idx = 0;
                    for (File vcf : files)
                    {
                        bashCommands.add("cat " + vcf.getPath() + (idx == 0 ? " > " : " | grep -v '#' >> ") + outputUnzip.getPath());
                        idx++;
                    }

                    try
                    {
                        File bashTmp = new File(ctx.getOutputDir(), "vcfCombine.sh");
                        ctx.getFileManager().addIntermediateFile(bashTmp);
                        try (PrintWriter writer = PrintWriters.getPrintWriter(bashTmp))
                        {
                            writer.write("#!/bin/bash\n");
                            writer.write("set -x\n");
                            writer.write("set -e\n");
                            bashCommands.forEach(x -> writer.write(x + '\n'));
                        }

                        SimpleScriptWrapper wrapper = new SimpleScriptWrapper(ctx.getLogger());
                        wrapper.execute(Arrays.asList("/bin/bash", bashTmp.getPath()));

                        File bg = SequenceAnalysisService.get().bgzipFile(outputUnzip, ctx.getLogger());
                        if (!bg.equals(cassandraAnnotated))
                        {
                            if (cassandraAnnotated.exists())
                            {
                                cassandraAnnotated.delete();
                            }

                            FileUtils.moveFile(bg, cassandraAnnotated);
                        }

                        SequenceAnalysisService.get().ensureVcfIndex(cassandraAnnotated, ctx.getLogger());

                        ctx.getJob().getLogger().info("total variants: " + SequenceAnalysisService.get().getVCFLineCount(cassandraAnnotated, ctx.getJob().getLogger(), false));
                        ctx.getJob().getLogger().info("passing variants: " + SequenceAnalysisService.get().getVCFLineCount(cassandraAnnotated, ctx.getJob().getLogger(), true));
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }
                }
                else
                {
                    ctx.getLogger().info("resuming with existing file: " + cassandraAnnotated.getPath());
                }
                ctx.getFileManager().addOutput(action, "VCF Annotated With Cassandra", cassandraAnnotated);
                ctx.getFileManager().addIntermediateFile(cassandraAnnotated);
                ctx.getFileManager().addIntermediateFile(new File(cassandraAnnotated.getPath() + ".tbi"));

                //re-add per-contig intermediates per chr, in case the job restarted:
                SAMSequenceDictionary dict = SAMSequenceDictionaryExtractor.extractDictionary(grch37Genome.getSequenceDictionary().toPath());
                for (SAMSequenceRecord seq : dict.getSequences())
                {
                    String basename = SequenceAnalysisService.get().getUnzippedBaseName(cassandraAnnotated.getName());
                    CassandraPerChrJob job = new CassandraPerChrJob(seq.getSequenceName(), cassandraAnnotated.getParentFile(), basename, grch37Genome, liftedToGRCh37, ctx.getFileManager(), ctx.getLogger());
                    job.addIntermediateFiles();
                }

                //backport ClinVar
                ctx.getLogger().info("backport ClinVar 2.0 to source genome");
                File clinvarAnnotatedBackport = new File(ctx.getOutputDir(), SequenceAnalysisService.get().getUnzippedBaseName(clinvarAnnotated.getName()) + ".bp.vcf.gz");
                if (!indexExists(clinvarAnnotatedBackport ))
                {
                    BackportLiftedVcfRunner bpRunner = new BackportLiftedVcfRunner(ctx.getLogger());
                    bpRunner.execute(clinvarAnnotated, originalGenome.getWorkingFastaFile(), clinvarAnnotatedBackport);
                }
                else
                {
                    ctx.getLogger().info("resuming with existing file: " + clinvarAnnotatedBackport.getPath());
                }
                ctx.getFileManager().addOutput(action, "VCF Annotated With Clinvar, Backported", clinvarAnnotatedBackport);
                ctx.getFileManager().addIntermediateFile(clinvarAnnotatedBackport);
                ctx.getFileManager().addIntermediateFile(new File(clinvarAnnotatedBackport.getPath() + ".tbi"));

                //backport Cassandra
                ctx.getLogger().info("backport Cassandra to source genome");
                File cassandraAnnotatedBackport = new File(ctx.getOutputDir(), SequenceAnalysisService.get().getUnzippedBaseName(cassandraAnnotated.getName()) + ".bp.vcf.gz");
                if (!indexExists(cassandraAnnotatedBackport))
                {
                    BackportLiftedVcfRunner bpRunner = new BackportLiftedVcfRunner(ctx.getLogger());
                    bpRunner.execute(cassandraAnnotated, originalGenome.getWorkingFastaFile(), cassandraAnnotatedBackport);
                }
                else
                {
                    ctx.getLogger().info("resuming with existing file: " + cassandraAnnotatedBackport.getPath());
                }
                ctx.getFileManager().addOutput(action, "VCF Annotated With Cassandra, Backported", cassandraAnnotatedBackport);
                ctx.getFileManager().addIntermediateFile(cassandraAnnotatedBackport);
                ctx.getFileManager().addIntermediateFile(new File(cassandraAnnotatedBackport.getPath() + ".tbi"));

                //multiannotator
                ctx.getLogger().info("Running MultiSourceAnnotator");
                File multiAnnotated = new File(ctx.getOutputDir(), SequenceAnalysisService.get().getUnzippedBaseName(so.getFile().getName()) + ".ma.vcf.gz");
                if (!indexExists(multiAnnotated))
                {
                    MultiSourceAnnotatorRunner maRunner = new MultiSourceAnnotatorRunner(ctx.getLogger());
                    maRunner.execute(so.getFile(), cassandraAnnotatedBackport, clinvarAnnotatedBackport, liftoverRejects, multiAnnotated);
                }
                else
                {
                    ctx.getLogger().info("resuming with existing file: " + multiAnnotated.getPath());
                }
                ctx.getFileManager().addOutput(action, "VCF Multi-Annotated", multiAnnotated);

                ctx.getJob().getLogger().info("total variants: " + SequenceAnalysisService.get().getVCFLineCount(multiAnnotated, ctx.getJob().getLogger(), false));
                ctx.getJob().getLogger().info("passing variants: " + SequenceAnalysisService.get().getVCFLineCount(multiAnnotated, ctx.getJob().getLogger(), true));

                //final output
                SequenceOutputFile output = new SequenceOutputFile();
                output.setFile(multiAnnotated);
                output.setName("Multi-Annotated VCF: " + so.getName());
                output.setCategory("Annotated VCF File");
                output.setLibrary_id(so.getLibrary_id());
                ctx.getFileManager().addSequenceOutput(output);
            }
        }

        private List<File> runCassandraPerChromosome(JobContext ctx, File liftedToGRCh37, File finalOutput, ReferenceGenome genome) throws PipelineJobException
        {
            String basename = SequenceAnalysisService.get().getUnzippedBaseName(finalOutput.getName());

            SAMSequenceDictionary dict = SAMSequenceDictionaryExtractor.extractDictionary(genome.getSequenceDictionary().toPath());
            //NOTE: need to scale RAM per job if running in parallel on the node
            Integer maxJobs = 1; //SequencePipelineService.get().getMaxThreads(ctx.getLogger());
            //if (maxJobs == null)
            //{
            //   maxJobs = 1;
            //}
            //
            //maxJobs--;
            //maxJobs = Math.max(1, maxJobs);
            //maxJobs = Math.min(12, maxJobs);  //keep concurrent reasonable for I/O

            JobRunner jobRunner = new JobRunner("CassandraRunner", maxJobs);
            List<CassandraPerChrJob> jobs = new ArrayList<>();
            for (SAMSequenceRecord seq : dict.getSequences())
            {
                CassandraPerChrJob job = new CassandraPerChrJob(seq.getSequenceName(), finalOutput.getParentFile(), basename, genome, liftedToGRCh37, ctx.getFileManager(), ctx.getLogger());
                jobs.add(job);
                jobRunner.execute(job);
            }

            ctx.getLogger().info("total jobs: " + jobRunner.getJobCount());
            jobRunner.waitForCompletion();
            ctx.getLogger().info("job runner complete");

            List<String> errors = new ArrayList<>();
            List<File> outputs = new ArrayList<>();
            for (CassandraPerChrJob job : jobs)
            {
                if (job._hadError)
                {
                    errors.add(job.getChr());
                }
                else
                {
                    if (job._hasVariants)
                    {
                        if (!job._cassandraOutputVcf.exists())
                        {
                            throw new PipelineJobException("Unable to find expected file: " + job._cassandraOutputVcf.getPath());
                        }

                        outputs.add(job._cassandraOutputVcf);
                    }
                    else
                    {
                        ctx.getLogger().debug("no variants found for chromosome: " + job.getChr());
                    }
                }
            }

            if (!errors.isEmpty())
            {
                throw new PipelineJobException("Probelm running cassandra for chromosomes: " + StringUtils.join(errors, "; "));
            }
            
            return outputs;
        }
    }

    public static class CassandraPerChrJob extends Job
    {
        private Logger _primaryLog;
        private String _chr;
        private File _subsetVcf;
        private File _cassandraOutputVcf;
        private ReferenceGenome _referenceGenome;
        private File _inputVcf;
        private boolean _hadError = false;
        private TaskFileManager _fileManager;
        private  File _logFile;
        private boolean _hasVariants = true;

        public CassandraPerChrJob(String chr, File outputDir, String outputBasename, ReferenceGenome genome, File inputVcf, TaskFileManager fileManager, Logger primaryLog)
        {
            _chr = chr;
            _subsetVcf = new File(outputDir, outputBasename + "." + _chr + ".subset.vcf");
            _cassandraOutputVcf = new File(outputDir, outputBasename + "." + _chr + ".cassandra.vcf");
            _inputVcf = inputVcf;
            _referenceGenome = genome;
            _fileManager = fileManager;
            _primaryLog = primaryLog;
            _logFile = new File(_cassandraOutputVcf.getParentFile(), outputBasename + "." + chr + ".log");
        }

        private Logger _logger = null;
        
        private Logger getLog()
        {
            if (_logger == null)
            {
                // Create appending logger.
                _logger = Logger.getLogger(CassandraPerChrJob.class);
                _logger.removeAllAppenders();
                
                SafeFileAppender appender = new SafeFileAppender(_logFile);
                appender.setLayout(new PatternLayout("%d{DATE} %-5p: %m%n"));
                _logger.addAppender(appender);
                _logger.setLevel(Level.ALL);
                //_logger.getLoggerRepository().setThreshold(Level.DEBUG);
            }
            
            return _logger;
        }
        
        @Override
        public void run()
        {
            getLog();

            try
            {
                _primaryLog.info("running cassandra for chromosome: " + _chr + ", with log: " + _logFile.getPath());
                getLog().info("running cassandra for chromosome: " + _chr);
                
                File subsetDone = new File(_subsetVcf + ".done");
                if (subsetDone.exists())
                {
                    _primaryLog.info("re-using subset vcf, " + _subsetVcf.getPath());
                }
                else
                {
                    //TODO: limit RAM?
                    SelectVariantsWrapper sv = new SelectVariantsWrapper(getLog());
                    sv.execute(_referenceGenome.getWorkingFastaFile(), _inputVcf, _subsetVcf, Arrays.asList("-L", _chr));

                    FileUtils.touch(subsetDone);

                    _primaryLog.info("finished subset for chromosome: " + _chr);
                }

                //verify variant count:
                _hasVariants = true;
                try (VCFFileReader reader = new VCFFileReader(_subsetVcf))
                {
                    try (CloseableIterator<VariantContext> it = reader.iterator())
                    {
                        if (!it.hasNext())
                        {
                            _hasVariants = false;
                        }
                    }
                }

                if (!_hasVariants)
                {
                    _primaryLog.info("no variants found for " + _chr + ", aborting");
                    return;
                }

                File cassandraDone = new File(_cassandraOutputVcf + ".done");
                if (cassandraDone.exists())
                {
                    _primaryLog.info("re-using cassandra vcf, " + _cassandraOutputVcf.getPath());
                }
                else
                {
                    List<String> extraArgs = new ArrayList<>();

                    //NOTE: Cassandra will not sort the output when multithreaded, so the extra sorting we would need to do negates any benefit here
                    //Integer threads = SequencePipelineService.get().getMaxThreads(ctx.getLogger());
                    //if (threads != null)
                    //{
                    //    extraArgs.add("-n");
                    //    extraArgs.add(threads.toString());
                    //}

                    String tmpDir = SequencePipelineService.get().getJavaTempDir();
                    if (!StringUtils.isEmpty(tmpDir))
                    {
                        extraArgs.add("--tempDir");
                        extraArgs.add(tmpDir);
                    }

                    CassandraRunner cassRunner = new CassandraRunner(getLog());
                    cassRunner.execute(_subsetVcf, _cassandraOutputVcf, extraArgs);

                    FileUtils.touch(cassandraDone);

                    _primaryLog.info("finished cassandra for chromosome: " + _chr);
                }

                addIntermediateFiles();
            }
            catch (PipelineJobException | IOException e)
            {
                _primaryLog.error("Error processing sequence: " + _chr, e);
                _hadError = true;

                getLog().error(e);
            }
        }

        public void addIntermediateFiles()
        {
            File subsetDone = new File(_subsetVcf + ".done");
            _fileManager.addIntermediateFile(subsetDone);
            _fileManager.addIntermediateFile(_subsetVcf);
            _fileManager.addIntermediateFile(new File(_subsetVcf.getPath() + ".idx"));

            File cassandraDone = new File(_cassandraOutputVcf + ".done");
            _fileManager.addIntermediateFile(cassandraDone);
            _fileManager.addIntermediateFile(_cassandraOutputVcf);
            _fileManager.addIntermediateFile(new File(_cassandraOutputVcf.getPath() + ".idx"));
        }

        public String getChr()
        {
            return _chr;
        }
    }

    protected static boolean indexExists(File vcf)
    {
        File idx = new File(vcf.getPath() + ".tbi");
        return idx.exists();
    }
}
