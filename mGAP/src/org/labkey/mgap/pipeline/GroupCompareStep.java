package org.labkey.mgap.pipeline;

import htsjdk.samtools.util.Interval;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractDiscvrSeqWrapper;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 12:39 PM
 */
public class GroupCompareStep extends AbstractCommandPipelineStep<GroupCompareStep.GroupComparison> implements VariantProcessingStep
{
    public static final String REF_VCF = "refVcf";
    public static String GROUP1 = "group1";
    public static String GROUP2 = "group2";


    public GroupCompareStep(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx, new GroupComparison(ctx.getLogger()));
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<GroupCompareStep> implements SupportsScatterGather
    {
        public Provider()
        {
            super("GroupCompare", "Group Comparison", "DISCVRseq/GroupCompare", "This is designed to help with sifting and prioritizing variants. It will generate a VCF limited to just the samples in group 1 (and group 2 if provided). It will compare the AF, N_HOMVAR, N_HOMREF, and N_HET within each group. If a reference VCF is provided (e.g., population-level data), these values will also be computed on that dataset. The resulting VCF is designed to be the starting point for secondary filtering.", List.of(
                    ToolParameterDescriptor.create(GROUP1, "Group 1 Sample(s)", "Only variants of the selected type(s) will be included", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("allowBlank", false);
                    }}, null),
                    ToolParameterDescriptor.create(GROUP2, "Group 2 Sample(s)", "Optional. Only variants of the selected type(s) will be included", "sequenceanalysis-trimmingtextarea", null, null),
                    ToolParameterDescriptor.createExpDataParam(REF_VCF, "Reference VCF", "This is the file ID of the VCF to use as the reference.", "ldk-expdatafield", new JSONObject()
                    {{
                        put("allowBlank", false);
                    }}, null)
            ), null, null);
        }

        @Override
        public GroupCompareStep create(PipelineContext ctx)
        {
            return new GroupCompareStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();
        getPipelineCtx().getLogger().info("Running GroupCompare");

        Integer refFileId = getProvider().getParameterByName(REF_VCF).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class);

        List<String> extraArgs = new ArrayList<>();
        if (intervals != null)
        {
            intervals.forEach(interval -> {
                extraArgs.add("-L");
                extraArgs.add(interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd());
            });
        }

        File refVcf = getPipelineCtx().getSequenceSupport().getCachedData(refFileId);
        if (refVcf == null || !refVcf.exists())
        {
            extraArgs.add("-RV");
            extraArgs.add(refVcf.getPath());
        }

        File outputVcf = new File(outputDirectory, SequenceAnalysisService.get().getUnzippedBaseName(inputVCF.getName()) + ".gc.vcf.gz");
        File outputTable = new File(outputDirectory, SequenceAnalysisService.get().getUnzippedBaseName(inputVCF.getName()) + ".gc.txt");

        getWrapper().runTool(inputVCF, refVcf, outputTable, genome.getWorkingFastaFile(), extraArgs);
        if (!outputTable.exists())
        {
            throw new PipelineJobException("Unable to find output: " + outputTable.getPath());
        }

        output.addInput(inputVCF, "Input VCF");
        output.addInput(genome.getWorkingFastaFile(), "Reference Genome");
        output.addOutput(outputTable, "GroupCompare Table");
        output.setVcf(outputVcf);

        return output;
    }

    public static class GroupComparison extends AbstractDiscvrSeqWrapper
    {
        public GroupComparison(Logger log)
        {
            super(log);
        }

        public void runTool(File inputVCF, File outputVcf, File outputTable, File genomeFasta, List<String> extraArgs) throws PipelineJobException
        {
            List<String> args = new ArrayList<>(getBaseArgs());
            args.add("GroupComparison");
            args.add("-R");
            args.add(genomeFasta.getPath());

            args.add("-V");
            args.add(inputVCF.getPath());

            args.add("-O");
            args.add(outputVcf.getPath());

            args.add("-OT");
            args.add(outputTable.getPath());

            args.add("--ignore-variants-starting-outside-interval");

            if (extraArgs != null)
            {
                args.addAll(extraArgs);
            }

            execute(args);
        }
    }
}
