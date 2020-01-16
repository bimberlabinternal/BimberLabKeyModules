package org.labkey.mgap.pipeline;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.Interval;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFFileReader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.SelectVariantsWrapper;
import org.labkey.api.util.Job;
import org.labkey.api.util.JobRunner;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SafeFileAppender;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by bimber on 5/2/2017.
 */
public class AnnotationStep extends AbstractCommandPipelineStep<CassandraRunner> implements VariantProcessingStep
{
    public static final String GRCH37 = "genome37";
    private static final String CLINVAR_VCF = "clinvar37";
    public static final String CHAIN_FILE = "CHAIN_FILE";

    public AnnotationStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new CassandraRunner(ctx.getLogger()));
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<AnnotationStep> implements VariantProcessingStep.SupportsScatterGather
    {
        public Provider()
        {
            super("AnnotateVariants", "Annotate VCF for mGAP", "VCF Annotation", "This will annotate an input NHP VCF using human ClinVar and Cassandra annotations.  This jobs will automatically look for chain files based on the source VCF genome and GRCh37/38 targets and will fail if these are not found.", Arrays.asList(
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
                    ToolParameterDescriptor.create("dropFiltered", "Drop Filtered Sites", "If checked, filtered sites will be discarded, which can substantially improve speed.", "checkbox", new JSONObject()
                    {{
                        put("checked", true);
                    }}, true)
            ), new LinkedHashSet<String>(Arrays.asList("ldk/field/ExpDataField.js")), null);
        }

        @Override
        public PipelineStep create(PipelineContext context)
        {
            return new AnnotationStep(this, context);
        }
    }


    @Override
    public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles) throws UnsupportedOperationException, PipelineJobException
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
        job.getLogger().debug("Caching references");
        support.cacheGenome(SequenceAnalysisService.get().getReferenceGenome(sourceGenome, job.getUser()));
        int genomeId = getProvider().getParameterByName(GRCH37).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class);
        support.cacheGenome(SequenceAnalysisService.get().getReferenceGenome(genomeId, job.getUser()));

        //find chain files:
        job.getLogger().debug("Caching chain file");
        findChainFile(sourceGenome, genomeId, support, job);
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();

        File clinvarVCF = getPipelineCtx().getSequenceSupport().getCachedData(getProvider().getParameterByName(CLINVAR_VCF).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class));
        ReferenceGenome grch37Genome = getPipelineCtx().getSequenceSupport().getCachedGenome(getProvider().getParameterByName(GRCH37).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class));
        Integer chainFileId = getPipelineCtx().getSequenceSupport().getCachedObject(CHAIN_FILE, Integer.class);
        File chainFile = getPipelineCtx().getSequenceSupport().getCachedData(chainFileId);

        getPipelineCtx().getLogger().info("processing file: " + inputVCF.getName());

        ReferenceGenome originalGenome = getPipelineCtx().getSequenceSupport().getCachedGenome(genome.getGenomeId());

        output.addInput(inputVCF, "Input VCF");
        output.addInput(new File(inputVCF.getPath() + ".tbi"), "Input VCF Index");

        //drop genotypes so all subsequent steps are faster
        int totalSubjects;
        String firstSample;
        try (VCFFileReader reader = new VCFFileReader(inputVCF))
        {
            totalSubjects = reader.getFileHeader().getSampleNamesInOrder().size();
            firstSample = reader.getFileHeader().getSampleNamesInOrder().get(0);
        }

        boolean needToSubsetToInterval = intervals != null && !intervals.isEmpty();
        boolean dropGenotypes = totalSubjects > 10;
        boolean dropFiltered = getProvider().getParameterByName("dropFiltered").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class);

        File currentVcf = inputVCF;
        if (dropGenotypes || dropFiltered)
        {
            if (dropGenotypes)
                getPipelineCtx().getLogger().info("dropping most genotypes prior to liftover for performance reasons.  a single is retained since cassandra requires one.");
            if (dropFiltered)
                getPipelineCtx().getLogger().info("dropping filtered sites");

            File subset = new File(outputDirectory, SequenceAnalysisService.get().getUnzippedBaseName(inputVCF.getName()) + ".subset.vcf.gz");
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
                    selectArgs.add("--exclude-filtered");
                }

                if (needToSubsetToInterval)
                {
                    for (Interval interval : intervals)
                    {
                        selectArgs.add("-L");
                        selectArgs.add(interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd());
                    }
                    needToSubsetToInterval = false;
                }

                SelectVariantsWrapper wrapper = new SelectVariantsWrapper(getPipelineCtx().getLogger());
                wrapper.execute(originalGenome.getWorkingFastaFile(), inputVCF, subset, selectArgs);
            }
            else
            {
                getPipelineCtx().getLogger().info("resuming with existing file: " + subset.getPath());
            }
            output.addOutput(subset, "VCF Subset");
            output.addIntermediateFile(subset);
            output.addIntermediateFile(new File(subset.getPath() + ".tbi"));

            currentVcf = subset;

            getPipelineCtx().getJob().getLogger().info("total variants: " + SequenceAnalysisService.get().getVCFLineCount(currentVcf, getPipelineCtx().getJob().getLogger(), false));
            getPipelineCtx().getJob().getLogger().info("passing variants: " + SequenceAnalysisService.get().getVCFLineCount(currentVcf, getPipelineCtx().getJob().getLogger(), true));
        }
        else
        {
            getPipelineCtx().getLogger().info("no subsetting of genotypes or filtered sites necessary");

            if (needToSubsetToInterval)
            {
                List<String> selectArgs = new ArrayList<>();
                getPipelineCtx().getLogger().info("subsetting VCF by interval");
                for (Interval interval : intervals)
                {
                    selectArgs.add("-L");
                    selectArgs.add(interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd());
                }
                needToSubsetToInterval = false;

                File intervalSubset = new File(outputDirectory, SequenceAnalysisService.get().getUnzippedBaseName(inputVCF.getName()) + ".intervalSubset.vcf.gz");
                if (!indexExists(intervalSubset))
                {
                    SelectVariantsWrapper wrapper = new SelectVariantsWrapper(getPipelineCtx().getLogger());
                    wrapper.execute(originalGenome.getWorkingFastaFile(), inputVCF, intervalSubset, selectArgs);
                }
                else
                {
                    getPipelineCtx().getLogger().info("resuming with existing file: " + intervalSubset.getPath());
                }

                output.addOutput(intervalSubset, "VCF Subset");
                output.addIntermediateFile(intervalSubset);
                output.addIntermediateFile(new File(intervalSubset.getPath() + ".tbi"));

                currentVcf = intervalSubset;

                getPipelineCtx().getJob().getLogger().info("total variants: " + SequenceAnalysisService.get().getVCFLineCount(currentVcf, getPipelineCtx().getJob().getLogger(), false));
                getPipelineCtx().getJob().getLogger().info("passing variants: " + SequenceAnalysisService.get().getVCFLineCount(currentVcf, getPipelineCtx().getJob().getLogger(), true));
            }
        }

        //lift to target genome
        getPipelineCtx().getLogger().info("lift to genome: " + grch37Genome.getGenomeId());

        File liftedToGRCh37 = new File(outputDirectory, SequenceAnalysisService.get().getUnzippedBaseName(currentVcf.getName()) + ".liftTo" + grch37Genome.getGenomeId() + ".vcf.gz");
        File liftoverRejects = new File(outputDirectory, SequenceAnalysisService.get().getUnzippedBaseName(currentVcf.getName()) + ".liftoverReject" + grch37Genome.getGenomeId() + ".vcf.gz");
        if (!indexExists(liftoverRejects))
        {
            LiftoverVcfRunner liftoverVcfRunner = new LiftoverVcfRunner(getPipelineCtx().getLogger());
            liftoverVcfRunner.doLiftover(currentVcf, chainFile, grch37Genome.getWorkingFastaFile(), liftoverRejects, liftedToGRCh37, 0.95);
        }
        else
        {
            getPipelineCtx().getLogger().info("resuming with existing file: " + liftedToGRCh37.getPath());
        }
        output.addOutput(liftedToGRCh37, "VCF Lifted to GRCh37");
        output.addIntermediateFile(liftedToGRCh37);
        output.addIntermediateFile(new File(liftedToGRCh37.getPath() + ".tbi"));

        //annotate with clinvar
        getPipelineCtx().getLogger().info("annotating with ClinVar 2.0");
        File clinvarAnnotated = new File(outputDirectory, SequenceAnalysisService.get().getUnzippedBaseName(liftedToGRCh37.getName()) + ".cv.vcf.gz");
        if (!indexExists(clinvarAnnotated))
        {
            ClinvarAnnotatorRunner cvRunner = new ClinvarAnnotatorRunner(getPipelineCtx().getLogger());
            cvRunner.execute(liftedToGRCh37, clinvarVCF, clinvarAnnotated);
        }
        else
        {
            getPipelineCtx().getLogger().info("resuming with existing file: " + clinvarAnnotated.getPath());
        }
        output.addOutput(clinvarAnnotated, "VCF Annotated With ClinVar2.0");
        output.addIntermediateFile(clinvarAnnotated);
        output.addIntermediateFile(new File(clinvarAnnotated.getPath() + ".tbi"));

        //annotate with cassandra
        getPipelineCtx().getLogger().info("annotating with Cassandra");
        String basename = SequenceAnalysisService.get().getUnzippedBaseName(liftedToGRCh37.getName()) + ".cassandra";
        File cassandraAnnotated = new File(outputDirectory, basename + ".vcf.gz");
        if (!indexExists(cassandraAnnotated))
        {
            List<File> files = runCassandraPerChromosome(liftedToGRCh37, cassandraAnnotated, grch37Genome, output, intervals);

            SequenceAnalysisService.get().combineVcfs(files, outputDirectory, basename, getPipelineCtx().getLogger());
        }
        else
        {
            getPipelineCtx().getLogger().info("resuming with existing file: " + cassandraAnnotated.getPath());
        }
        output.addOutput(cassandraAnnotated, "VCF Annotated With Cassandra");
        output.addIntermediateFile(cassandraAnnotated);
        output.addIntermediateFile(new File(cassandraAnnotated.getPath() + ".tbi"));

        //re-add per-contig intermediates per chr, in case the job restarted:
        SAMSequenceDictionary dict = SAMSequenceDictionaryExtractor.extractDictionary(grch37Genome.getSequenceDictionary().toPath());
        for (SAMSequenceRecord seq : dict.getSequences())
        {
            CassandraPerChrJob job = new CassandraPerChrJob(seq.getSequenceName(), cassandraAnnotated.getParentFile(), basename, grch37Genome, liftedToGRCh37, output, getPipelineCtx().getLogger(), null, null);  //the memory override is moot since it wont be run
            job.addIntermediateFiles();
        }

        //backport ClinVar
        getPipelineCtx().getLogger().info("backport ClinVar 2.0 to source genome");
        File clinvarAnnotatedBackport = new File(outputDirectory, SequenceAnalysisService.get().getUnzippedBaseName(clinvarAnnotated.getName()) + ".bp.vcf.gz");
        if (!indexExists(clinvarAnnotatedBackport ))
        {
            BackportLiftedVcfRunner bpRunner = new BackportLiftedVcfRunner(getPipelineCtx().getLogger());
            bpRunner.execute(clinvarAnnotated, originalGenome.getWorkingFastaFile(), grch37Genome.getWorkingFastaFile(), clinvarAnnotatedBackport);
        }
        else
        {
            getPipelineCtx().getLogger().info("resuming with existing file: " + clinvarAnnotatedBackport.getPath());
        }
        output.addOutput(clinvarAnnotatedBackport, "VCF Annotated With Clinvar, Backported");
        output.addIntermediateFile(clinvarAnnotatedBackport);
        output.addIntermediateFile(new File(clinvarAnnotatedBackport.getPath() + ".tbi"));

        //backport Cassandra
        getPipelineCtx().getLogger().info("backport Cassandra to source genome");
        File cassandraAnnotatedBackport = new File(outputDirectory, SequenceAnalysisService.get().getUnzippedBaseName(cassandraAnnotated.getName()) + ".bp.vcf.gz");
        if (!indexExists(cassandraAnnotatedBackport))
        {
            BackportLiftedVcfRunner bpRunner = new BackportLiftedVcfRunner(getPipelineCtx().getLogger());
            bpRunner.execute(cassandraAnnotated, originalGenome.getWorkingFastaFile(), grch37Genome.getWorkingFastaFile(), cassandraAnnotatedBackport);
        }
        else
        {
            getPipelineCtx().getLogger().info("resuming with existing file: " + cassandraAnnotatedBackport.getPath());
        }
        output.addOutput(cassandraAnnotatedBackport, "VCF Annotated With Cassandra, Backported");
        output.addIntermediateFile(cassandraAnnotatedBackport);
        output.addIntermediateFile(new File(cassandraAnnotatedBackport.getPath() + ".tbi"));

        //multiannotator
        getPipelineCtx().getLogger().info("Running MultiSourceAnnotator");
        File multiAnnotated = new File(getPipelineCtx().getWorkingDirectory(), SequenceAnalysisService.get().getUnzippedBaseName(inputVCF.getName()) + ".ma.vcf.gz");
        if (!indexExists(multiAnnotated))
        {
            MultiSourceAnnotatorRunner maRunner = new MultiSourceAnnotatorRunner(getPipelineCtx().getLogger());

            List<String> options = new ArrayList<>();
            if (needToSubsetToInterval)
            {
                for (Interval interval : intervals)
                {
                    options.add("-L");
                    options.add(interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd());
                }
                needToSubsetToInterval = false;
            }

            maRunner.execute(inputVCF, cassandraAnnotatedBackport, clinvarAnnotatedBackport, liftoverRejects, multiAnnotated, options);
        }
        else
        {
            getPipelineCtx().getLogger().info("resuming with existing file: " + multiAnnotated.getPath());
        }
        output.addOutput(multiAnnotated, "VCF Multi-Annotated");

        getPipelineCtx().getJob().getLogger().info("total variants: " + SequenceAnalysisService.get().getVCFLineCount(multiAnnotated, getPipelineCtx().getJob().getLogger(), false));
        getPipelineCtx().getJob().getLogger().info("passing variants: " + SequenceAnalysisService.get().getVCFLineCount(multiAnnotated, getPipelineCtx().getJob().getLogger(), true));

        //final output
        output.setVcf(multiAnnotated);

        return output;
    }

    private List<File> runCassandraPerChromosome(File liftedToGRCh37, File finalOutput, ReferenceGenome genome, VariantProcessingStepOutputImpl output, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        String basename = SequenceAnalysisService.get().getUnzippedBaseName(finalOutput.getName());

        SAMSequenceDictionary dict = SAMSequenceDictionaryExtractor.extractDictionary(genome.getSequenceDictionary().toPath());
        Integer threads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger());

        //NOTE: need to scale RAM per job if running in parallel on the node.  Currently cap at 4 concurrent
        Integer maxJobs = threads == null ? 1 : Math.min(4, threads);

        Integer maxRamPerJob = null;
        if (threads != null)
        {
            Integer maxRam = SequencePipelineService.get().getMaxRam();
            if (maxRam != null)
            {
                maxRamPerJob = Double.valueOf(Math.floor(maxRam / threads)).intValue();
                if (maxRamPerJob < 12)
                {
                    getPipelineCtx().getLogger().info("lowering number of concurrent jobs to ensure 12GB RAM/ea");
                    maxRamPerJob = 12;
                    maxJobs = Double.valueOf(Math.floor(maxRam / (double)maxRamPerJob)).intValue();
                }
            }
        }

        getPipelineCtx().getLogger().info("max concurrent jobs: " + maxJobs);
        getPipelineCtx().getLogger().info("max RAM per job: " + maxRamPerJob);

        JobRunner jobRunner = new JobRunner("CassandraRunner", maxJobs);
        List<CassandraPerChrJob> jobs = new ArrayList<>();
        File cassdandraLogDir = new File(getPipelineCtx().getSourceDirectory(true), "cassandra");
        if (!cassdandraLogDir.exists())
        {
            cassdandraLogDir.mkdirs();
        }

        Set<String> contigs = new HashSet<>();
        if (intervals == null || intervals.isEmpty())
        {
            dict.getSequences().forEach(seq -> contigs.add(seq.getSequenceName()));
        }
        else
        {
            intervals.forEach(interval -> contigs.add(dict.getSequence(interval.getContig()).getSequenceName()));
        }

        getPipelineCtx().getLogger().debug("Total contigs for cassandra: " + contigs.size());

        for (String contig : contigs)
        {
            CassandraPerChrJob job = new CassandraPerChrJob(contig, finalOutput.getParentFile(), basename, genome, liftedToGRCh37, output, getPipelineCtx().getLogger(), maxRamPerJob, cassdandraLogDir);
            jobs.add(job);
            jobRunner.execute(job);
        }

        getPipelineCtx().getLogger().info("total jobs: " + jobRunner.getJobCount());
        jobRunner.waitForCompletion();
        getPipelineCtx().getLogger().info("job runner complete");

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
                    getPipelineCtx().getLogger().debug("no variants found for chromosome: " + job.getChr());
                }
            }
        }

        if (!errors.isEmpty())
        {
            throw new PipelineJobException("Probelm running cassandra for chromosomes: " + StringUtils.join(errors, "; "));
        }

        return outputs;
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
        private VariantProcessingStepOutputImpl _variantOutput;
        private  File _logFile;
        private boolean _hasVariants = true;
        private Integer _maxRamOverride = null;

        public CassandraPerChrJob(String chr, File outputDir, String outputBasename, ReferenceGenome genome, File inputVcf, VariantProcessingStepOutputImpl variantOutput, Logger primaryLog, Integer maxRamOverride, @Nullable File cassdandraLogDir)
        {
            _chr = chr;
            _subsetVcf = new File(outputDir, outputBasename + "." + _chr + ".subset.vcf");
            _cassandraOutputVcf = new File(outputDir, outputBasename + "." + _chr + ".cassandra.vcf");
            _inputVcf = inputVcf;
            _referenceGenome = genome;
            _variantOutput = variantOutput;
            _primaryLog = primaryLog;
            _logFile = new File(cassdandraLogDir == null ? _cassandraOutputVcf.getParentFile() : cassdandraLogDir, outputBasename + "." + chr + ".log");
            _maxRamOverride = maxRamOverride;
        }

        private Logger _logger = null;

        private Logger getLog()
        {
            if (_logger == null)
            {
                // Create appending logger.
                _logger = Logger.getLogger(CassandraPerChrJob.class.getName() + "|" + _chr);
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
                    SelectVariantsWrapper sv = new SelectVariantsWrapper(getLog());
                    sv.setMaxRamOverride(_maxRamOverride);
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
                    //Integer threads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger());
                    //if (threads != null)
                    //{
                    //    extraArgs.add("-n");
                    //    extraArgs.add(threads.toString());
                    //}

                    String tmpDir = SequencePipelineService.get().getJavaTempDir();
                    if (!StringUtils.isEmpty(tmpDir))
                    {
                        File tmpDirFile = new File(tmpDir, "cassandra-" + _chr);
                        if (!tmpDirFile.exists())
                        {
                            tmpDirFile.mkdirs();
                        }

                        extraArgs.add("--tempDir");
                        extraArgs.add(tmpDirFile.getPath());
                    }

                    CassandraRunner cassRunner = new CassandraRunner(getLog());
                    cassRunner.setMaxRamOverride(_maxRamOverride);
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
            _variantOutput.addIntermediateFile(subsetDone);
            _variantOutput.addIntermediateFile(_subsetVcf);
            _variantOutput.addIntermediateFile(new File(_subsetVcf.getPath() + ".idx"));

            File cassandraDone = new File(_cassandraOutputVcf + ".done");
            _variantOutput.addIntermediateFile(cassandraDone);
            _variantOutput.addIntermediateFile(_cassandraOutputVcf);
            _variantOutput.addIntermediateFile(new File(_cassandraOutputVcf.getPath() + ".idx"));
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

    public static void findChainFile(int sourceGenome, int targetGenome, SequenceAnalysisJobSupport support, PipelineJob job) throws PipelineJobException
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
}
