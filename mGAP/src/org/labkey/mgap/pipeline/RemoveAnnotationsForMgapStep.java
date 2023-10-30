package org.labkey.mgap.pipeline;

import htsjdk.samtools.util.Interval;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractDiscvrSeqWrapper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.mgap.mGAPSchema;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RemoveAnnotationsForMgapStep extends AbstractCommandPipelineStep<RemoveAnnotationsForMgapStep.RemoveAnnotationsWrapper> implements VariantProcessingStep
{
    public RemoveAnnotationsForMgapStep(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx, new RemoveAnnotationsWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<RemoveAnnotationsForMgapStep> implements VariantProcessingStep.SupportsScatterGather
    {
        public Provider()
        {
            super("RemoveAnnotationsForMgap", "Remove Annotations For mGAP", "RemoveAnnotations", "This will remove annotations from the selected VCF, limiting to only those expected for mGAP.", List.of(), null, null);
        }

        @Override
        public PipelineStep create(PipelineContext context)
        {
            return new RemoveAnnotationsForMgapStep(this, context);
        }
    }

    private static final String INFO_FIELDS = "infoFields";

    @Override
    public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles) throws PipelineJobException
    {
        // find/cache annotations:
        Container targetContainer = getPipelineCtx().getJob().getContainer().isWorkbook() ? getPipelineCtx().getJob().getContainer().getParent() : getPipelineCtx().getJob().getContainer();
        ArrayList<String> infoFields = new TableSelector(QueryService.get().getUserSchema(getPipelineCtx().getJob().getUser(), targetContainer, mGAPSchema.NAME).getTable(mGAPSchema.TABLE_VARIANT_ANNOTATIONS), PageFlowUtil.set("infoKey"), new SimpleFilter(FieldKey.fromString("category"), "Legacy Fields", CompareType.NEQ_OR_NULL), null).getArrayList(String.class);

        getPipelineCtx().getSequenceSupport().cacheObject(INFO_FIELDS, infoFields);
    }

    private List<String> getInfoFields() throws PipelineJobException
    {
        return getPipelineCtx().getSequenceSupport().getCachedObject(INFO_FIELDS, PipelineJob.createObjectMapper().getTypeFactory().constructParametricType(List.class, String.class));
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();

        File outputFile = new File(outputDirectory, SequenceAnalysisService.get().getUnzippedBaseName(inputVCF.getName()) + ".noAnnotations.vcf.gz");
        if (indexExists(outputFile))
        {
            getPipelineCtx().getLogger().info("re-using existing output: " + outputFile.getPath());
        }
        else
        {
            List<String> args = new ArrayList<>();
            List<String> infoFields = getInfoFields();
            getPipelineCtx().getLogger().info("Total INFO fields to retain: " + infoFields.size());
            if (infoFields.isEmpty())
            {
                throw new PipelineJobException("Info fields is empty");
            }

            for (String key : infoFields)
            {
                args.add("-A");
                args.add(key);
            }

            args.add("-ef");
            args.add("--clearGenotypeFilter");

            getWrapper().execute(inputVCF, outputFile, genome.getWorkingFastaFile(), args, intervals);
        }

        output.setVcf(outputFile);
        output.addIntermediateFile(outputFile);
        output.addIntermediateFile(new File(outputFile.getPath() + ".tbi"));

        return output;
    }

    private boolean indexExists(File vcf)
    {
        return new File(vcf.getPath() + ".tbi").exists();
    }

    public static class RemoveAnnotationsWrapper extends AbstractDiscvrSeqWrapper
    {
        public RemoveAnnotationsWrapper(Logger log)
        {
            super(log);
        }

        public void execute(File input, File outputFile, File referenceFasta, List<String> extraArgs, @Nullable List<Interval> intervals) throws PipelineJobException
        {
            List<String> args = new ArrayList<>(getBaseArgs());
            args.add("RemoveAnnotations");
            args.add("-R");
            args.add(referenceFasta.getPath());
            args.add("-V");
            args.add(input.getPath());
            args.add("-O");
            args.add(outputFile.getPath());

            if (intervals != null)
            {
                intervals.forEach(interval -> {
                    args.add("-L");
                    args.add(interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd());
                });
            }

            if (extraArgs != null)
            {
                args.addAll(extraArgs);
            }

            super.execute(args);
        }
    }
}
