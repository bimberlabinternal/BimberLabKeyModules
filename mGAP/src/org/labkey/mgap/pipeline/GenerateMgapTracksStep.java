package org.labkey.mgap.pipeline;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.util.Interval;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.TaskFileManager;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.api.sequenceanalysis.run.AbstractDiscvrSeqWrapper;
import org.labkey.api.sequenceanalysis.run.SelectVariantsWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.mgap.mGAPSchema;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class GenerateMgapTracksStep extends AbstractPipelineStep implements VariantProcessingStep, VariantProcessingStep.SupportsScatterGather
{
    public static final String TRACK_CATEGORY = "mGAP Release Track";

    // 1) makes the subset VCF per track with those IDs,
    // 2) dies if it cannot find any of the IDs being requested,
    // 3) dies if the VCF is using real IDs and not mGAP IDs,
    // 4) auto-updates the "tracks per release" table so this points to the newly updated track VCF

    public GenerateMgapTracksStep(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<GenerateMgapTracksStep> implements SupportsScatterGather
    {
        public Provider()
        {
            super("GenerateMgapTracksStep", "Generate mGAP Tracks", "GenerateMgapTracksStep", "This will use the set of sample IDs from the table mgap.releaseTrackSubsets to subset the input VCF and produce one VCF per track. It will perform basic validation and also update mgap.releaseTracks.", Arrays.asList(
                    ToolParameterDescriptor.create("releaseVersion", "mGAP Version", "This is the string that was used to annotate novel variants.", "textfield", new JSONObject(){{
                        put("allowBlank", false);
                        put("doNotIncludeInTemplates", true);
                    }}, null)
            ), null, null);
        }

        @Override
        public PipelineStep create(PipelineContext context)
        {
            return new GenerateMgapTracksStep(this, context);
        }
    }

    @Override
    public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles) throws PipelineJobException
    {
        if (inputFiles.size() != 1)
        {
            throw new PipelineJobException("This step expects to have a single VCF input");
        }

        SequenceOutputFile so = inputFiles.get(0);

        // Verify all IDs in header are mGAP aliases. This map is the true ID to mGAP alias
        Map<String, String> sampleIdToMgapAlias = getSampleToAlias(so.getFile());

        // Now read track list, validate IDs present, and write to file:
        TableInfo ti = QueryService.get().getUserSchema(getPipelineCtx().getJob().getUser(), (getPipelineCtx().getJob().getContainer().isWorkbook() ? getPipelineCtx().getJob().getContainer().getParent() : getPipelineCtx().getJob().getContainer()), mGAPSchema.NAME).getTable(mGAPSchema.TABLE_RELEASE_TRACK_SUBSETS);
        TableSelector ts = new TableSelector(ti, PageFlowUtil.set("trackName", "subjectId"));
        Set<String> requestedNotInVcf = new HashSet<>();
        Map<String, Set<String>> trackToSubject = new HashMap<>();
        ts.forEachResults(rs -> {
            if (!trackToSubject.containsKey(rs.getString(FieldKey.fromString("trackName"))))
            {
                trackToSubject.put(rs.getString(FieldKey.fromString("trackName")), new TreeSet<>());
            }

            String mgapAlias = sampleIdToMgapAlias.get(rs.getString(FieldKey.fromString("subjectId")));
            if (mgapAlias == null)
            {
                requestedNotInVcf.add(rs.getString(FieldKey.fromString("trackName")) + ": " + rs.getString(FieldKey.fromString("subjectId")));
                return;
            }

            trackToSubject.get(rs.getString(FieldKey.fromString("trackName"))).add(mgapAlias);
        });

        if (!requestedNotInVcf.isEmpty())
        {
            throw new IllegalArgumentException("The following track/sample pairs were requested but not in the VCF. Please check the source table: " + StringUtils.join(requestedNotInVcf, ", "));
        }

        File outputFile = getSampleNameFile(getPipelineCtx().getSourceDirectory(true));
        getPipelineCtx().getLogger().debug("caching mGAP tracks to file: " + outputFile.getPath() + ", total: "+ trackToSubject.size());
        try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(outputFile), '\t', CSVWriter.NO_QUOTE_CHARACTER))
        {
            for (String trackName : trackToSubject.keySet())
            {
                getPipelineCtx().getLogger().info(trackToSubject + ": " + trackToSubject.get(trackName).size());
                trackToSubject.get(trackName).forEach(x -> {
                    writer.writeNext(new String[]{trackName, x});
                });
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private File getNovelSitesOutput(File outputDirectory)
    {
        String releaseVersion = getProvider().getParameterByName("releaseVersion").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class);
        return new File(outputDirectory, "mGAP_v" + releaseVersion + "_NovelSites.vcf.gz");
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();
        Map<String, List<String>> trackToSamples = parseSampleMap(getSampleNameFile(getPipelineCtx().getSourceDirectory(true)));

        VCFHeader header;
        try (VCFFileReader reader = new VCFFileReader(inputVCF))
        {
            header = reader.getFileHeader();
        }

        if (!header.hasInfoLine("mGAPV"))
        {
            throw new IllegalStateException("VCF is missing the annotation: mGAPV");
        }

        processTracks(output, inputVCF, trackToSamples, outputDirectory, genome, intervals);

        // Also create the Novel Sites track:
        String releaseVersion = getProvider().getParameterByName("releaseVersion").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class);
        File novelSitesOutput = getNovelSitesOutput(outputDirectory);
        if (new File(novelSitesOutput.getPath() + ".tbi").exists())
        {
            getPipelineCtx().getLogger().debug("Index exists, will not remake novel sites VCF");
        }
        else
        {
            getPipelineCtx().getJob().setStatus(PipelineJob.TaskStatus.running, "Processing novel sites track");

            SelectVariantsWrapper sv = new SelectVariantsWrapper(getPipelineCtx().getLogger());
            List<String> svArgs = new ArrayList<>();
            svArgs.add("-select");
            svArgs.add("mGAPV == '" + releaseVersion + "'");
            if (intervals != null)
            {
                intervals.forEach(interval -> {
                    svArgs.add("-L");
                    svArgs.add(interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd());
                });
            }

            sv.execute(genome.getWorkingFastaFile(), inputVCF, novelSitesOutput, svArgs);
        }

        getPipelineCtx().getJob().getLogger().info("total variants: " + SequenceAnalysisService.get().getVCFLineCount(novelSitesOutput, getPipelineCtx().getJob().getLogger(), false));

        return output;
    }

    private File getOutputVcf(String trackName, File outputDirectory)
    {
        return new File(outputDirectory, FileUtil.makeLegalName(trackName) + ".vcf.gz");
    }

    @Override
    public void complete(PipelineJob job, List<SequenceOutputFile> inputs, List<SequenceOutputFile> outputsCreated, SequenceAnalysisJobSupport support) throws PipelineJobException
    {
        job.getLogger().debug("Updating tracks using outputs: " + outputsCreated.size());
        for (SequenceOutputFile so : outputsCreated)
        {
            if (!TRACK_CATEGORY.equals(so.getCategory()))
            {
                continue;
            }

            createOrUpdateTrack(so, job);
        }

        createOrUpdatePrimaryTrack(inputs.get(0), job);
    }

    private void createOrUpdatePrimaryTrack(SequenceOutputFile so, PipelineJob job) throws PipelineJobException
    {
        createOrUpdateTrack(so, job, "mGAP Release", true);
    }

    private void createOrUpdateTrack(SequenceOutputFile so, PipelineJob job) throws PipelineJobException
    {
        createOrUpdateTrack(so, job, so.getName(), false);
    }

    private void createOrUpdateTrack(SequenceOutputFile so, PipelineJob job, String trackName, boolean isPrimaryTrack) throws PipelineJobException
    {
        try
        {
            Container targetContainer = job.getContainer().isWorkbook() ? job.getContainer().getParent() : job.getContainer();
            TableInfo releaseTracks = QueryService.get().getUserSchema(job.getUser(), targetContainer, mGAPSchema.NAME).getTable(mGAPSchema.TABLE_RELEASE_TRACKS);
            TableSelector ts = new TableSelector(releaseTracks, PageFlowUtil.set("rowid"), new SimpleFilter(FieldKey.fromString("trackName"), trackName), null);
            if (!ts.exists())
            {
                job.getLogger().debug("Creating new track: " + trackName + " / " + so.getName());
                Map<String, Object> newRow = new CaseInsensitiveHashMap<>();
                newRow.put("trackName", trackName);
                newRow.put("label", trackName);
                newRow.put("vcfId", so.getRowid());
                newRow.put("isprimarytrack", isPrimaryTrack);

                BatchValidationException bve = new BatchValidationException();
                releaseTracks.getUpdateService().insertRows(job.getUser(), targetContainer, Arrays.asList(newRow), bve, null, null);
                if (bve.hasErrors())
                {
                    throw bve;
                }
            }
            else
            {
                int rowId = ts.getObject(Integer.class);
                job.getLogger().debug("Updating existing track: " + so.getName() + " / " + rowId);

                Map<String, Object> toUpdate = new CaseInsensitiveHashMap<>();
                toUpdate.put("rowId", rowId);
                toUpdate.put("vcfId", so.getRowid());

                Map<String, Object> oldKeys = new CaseInsensitiveHashMap<>();
                oldKeys.put("rowId", rowId);

                releaseTracks.getUpdateService().updateRows(job.getUser(), targetContainer, Arrays.asList(toUpdate), Arrays.asList(oldKeys), null, null);
            }
        }
        catch (QueryUpdateServiceException | SQLException | BatchValidationException | DuplicateKeyException | InvalidKeyException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private boolean indexExists(File vcf)
    {
        return new File(vcf.getPath() + ".tbi").exists();
    }

    private File getSampleNameFile(File outputDir)
    {
        return new File(outputDir, "sampleMapping.txt");
    }

    private Map<String, File> processTracks(VariantProcessingStepOutputImpl output, File currentVCF, Map<String, List<String>> trackToSamples, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        getPipelineCtx().getJob().setStatus(PipelineJob.TaskStatus.running, "Preparing release tracks");
        File sampleFile = new File(outputDirectory, "samples.txt");
        output.addIntermediateFile(sampleFile);

        try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(sampleFile), '\t', CSVWriter.NO_QUOTE_CHARACTER))
        {
            for (String trackName : trackToSamples.keySet())
            {
                File outputVcf = getOutputVcf(trackName, outputDirectory);
                trackToSamples.get(trackName).forEach(s -> {
                    writer.writeNext(new String[]{outputVcf.getPath(), s});
                });
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        List<String> options = new ArrayList<>();
        options.add("--remove-unused-alternates");

        options.add("--sample-mapping-file");
        options.add(sampleFile.getPath());

        options.add("--exclude-non-variants");

        if (intervals != null)
        {
            intervals.forEach(interval -> {
                options.add("-L");
                options.add(interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd());
            });
        }

        new SplitVcfBySamplesWrapper(getPipelineCtx().getLogger()).execute(currentVCF, outputDirectory, options);

        Map<String, File> outputs = new HashMap<>();
        for (String trackName : trackToSamples.keySet())
        {
            File vcf = getOutputVcf(trackName, outputDirectory);
            if (!vcf.exists())
            {
                throw new PipelineJobException("Missing expected file: " + vcf.getPath());
            }

            outputs.put(trackName, vcf);
        }

        return outputs;
    }

    private Map<String, List<String>> parseSampleMap(File sampleMapFile) throws PipelineJobException
    {
        Map<String, List<String>> ret = new TreeMap<>();
        try (CSVReader reader = new CSVReader(Readers.getReader(sampleMapFile), '\t'))
        {
            String[] line;
            while ((line = reader.readNext()) != null)
            {
                String trackName = line[0];
                if (!ret.containsKey(trackName))
                {
                    ret.put(trackName, new ArrayList<>());
                }

                ret.get(trackName).add(line[1]);
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return ret;
    }

    private Map<String, String> getSampleToAlias(File input) throws PipelineJobException
    {
        Map<String, String> trueIdToMgapId = new HashMap<>();
        Map<String, String> mGapIdToTrueId = new HashMap<>();
        try
        {
            SequenceAnalysisService.get().ensureVcfIndex(input, getPipelineCtx().getLogger());
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        try (VCFFileReader reader = new VCFFileReader(input))
        {
            VCFHeader header = reader.getFileHeader();
            List<String> allMgapIds = header.getSampleNamesInOrder();
            if (allMgapIds.isEmpty())
            {
                return Collections.emptyMap();
            }

            getPipelineCtx().getLogger().info("total samples in input VCF: " + allMgapIds.size());

            // validate all VCF samples are using aliases:
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("externalAlias"), allMgapIds, CompareType.IN);
            TableInfo ti = QueryService.get().getUserSchema(getPipelineCtx().getJob().getUser(), (getPipelineCtx().getJob().getContainer().isWorkbook() ? getPipelineCtx().getJob().getContainer().getParent() : getPipelineCtx().getJob().getContainer()), mGAPSchema.NAME).getTable(mGAPSchema.TABLE_ANIMAL_MAPPING);
            TableSelector ts = new TableSelector(ti, PageFlowUtil.set("subjectname", "externalAlias"), new SimpleFilter(filter), null);
            ts.forEachResults(rs -> {
                trueIdToMgapId.put(rs.getString(FieldKey.fromString("subjectname")), rs.getString(FieldKey.fromString("externalAlias")));
                mGapIdToTrueId.put(rs.getString(FieldKey.fromString("externalAlias")), rs.getString(FieldKey.fromString("subjectname")));
            });

            List<String> missingSamples = new ArrayList<>(allMgapIds);
            missingSamples.removeAll(mGapIdToTrueId.keySet());
            if (!missingSamples.isEmpty())
            {
                throw new PipelineJobException("The following samples in this VCF do not match known mGAP IDs: " + StringUtils.join(missingSamples, ", "));
            }

            // Now ensure we dont have duplicate mappings:
            List<String> translated = new ArrayList<>(header.getSampleNamesInOrder().stream().map(mGapIdToTrueId::get).toList());
            Set<String> unique = new HashSet<>();
            List<String> duplicates = translated.stream().filter(o -> !unique.add(o)).toList();
            if (!duplicates.isEmpty())
            {
                throw new PipelineJobException("One or more mGAP IDs referred to the same sample. They were: " + StringUtils.join(duplicates, ","));
            }
        }

        return trueIdToMgapId;
    }

    @Override
    public void performAdditionalMergeTasks(SequenceOutputHandler.JobContext ctx, PipelineJob job, TaskFileManager manager, ReferenceGenome genome, List<File> orderedScatterOutputs, List<String> orderedJobDirs) throws PipelineJobException
    {
        job.getLogger().info("Merging additional track VCFs");
        Map<String, List<String>> trackToSamples = parseSampleMap(getSampleNameFile(getPipelineCtx().getSourceDirectory(true)));
        for (String trackName : trackToSamples.keySet())
        {
            job.getLogger().debug("Merging track: " + trackName);
            List<File> toConcat = orderedJobDirs.stream().map(dirName -> {
                File f = getOutputVcf(trackName, new File(ctx.getSourceDirectory(), dirName));
                if (!f.exists())
                {
                    throw new IllegalStateException("Missing file: " + f.getPath());
                }

                ctx.getFileManager().addIntermediateFile(f);
                ctx.getFileManager().addIntermediateFile(new File(f.getPath() + ".tbi"));

                return f;
            }).toList();
            job.getLogger().debug("Total VCFs to merge: " + toConcat.size());
            if (toConcat.isEmpty())
            {
                throw new PipelineJobException("No VCFs found for track: " + trackName);
            }

            String basename = SequenceAnalysisService.get().getUnzippedBaseName(toConcat.get(0).getName());
            File combined = new File(ctx.getSourceDirectory(), basename + ".vcf.gz");
            File combinedIdx = new File(combined.getPath() + ".tbi");
            if (combinedIdx.exists())
            {
                job.getLogger().info("VCF exists, will not recreate: " + combined.getPath());
            }
            else
            {
                combined = SequenceAnalysisService.get().combineVcfs(toConcat, combined, genome, job.getLogger(), true, null);
            }

            SequenceOutputFile so = new SequenceOutputFile();
            so.setName(trackName);
            so.setFile(combined);
            so.setCategory(TRACK_CATEGORY);
            so.setLibrary_id(genome.getGenomeId());
            so.setDescription("mGAP track: " + trackName + ", total samples: " + trackToSamples.get(trackName).size());
            manager.addSequenceOutput(so);
        }

        job.getLogger().info("Merging novel sites VCF");
        List<File> toConcat = orderedJobDirs.stream().map(dirName -> {
            File f = getNovelSitesOutput(new File(ctx.getSourceDirectory(), dirName));
            if (!f.exists())
            {
                throw new IllegalStateException("Missing file: " + f.getPath());
            }

            ctx.getFileManager().addIntermediateFile(f);
            ctx.getFileManager().addIntermediateFile(new File(f.getPath() + ".tbi"));

            return f;
        }).toList();

        if (toConcat.isEmpty())
        {
            throw new PipelineJobException("No novel sites VCFs found");
        }

        String basename = SequenceAnalysisService.get().getUnzippedBaseName(toConcat.get(0).getName());
        File combined = new File(ctx.getSourceDirectory(), basename + ".vcf.gz");
        File combinedIdx = new File(combined.getPath() + ".tbi");
        if (combinedIdx.exists())
        {
            job.getLogger().info("VCF exists, will not recreate: " + combined.getPath());
        }
        else
        {
            combined = SequenceAnalysisService.get().combineVcfs(toConcat, combined, genome, job.getLogger(), true, null);
        }

        SequenceOutputFile so = new SequenceOutputFile();
        so.setName("Novel Sites in This Release");
        so.setFile(combined);
        so.setCategory(TRACK_CATEGORY);
        so.setLibrary_id(genome.getGenomeId());
        String releaseVersion = getProvider().getParameterByName("releaseVersion").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class);
        so.setDescription("These are novel sites in mGAP v" + releaseVersion);
        manager.addSequenceOutput(so);
    }

    public static class SplitVcfBySamplesWrapper extends AbstractDiscvrSeqWrapper
    {
        public SplitVcfBySamplesWrapper(Logger log)
        {
            super(log);
        }

        public void execute(File inputVCF, File outputDirectory, List<String> options) throws PipelineJobException
        {
            List<String> args = new ArrayList<>(getBaseArgs());
            args.add("SplitVcfBySamples");
            args.add("-V");
            args.add(inputVCF.getPath());
            args.add("-O");
            args.add(outputDirectory.getPath());

            if (options != null)
            {
                args.addAll(options);
            }

            execute(args);
        }
    }
}
