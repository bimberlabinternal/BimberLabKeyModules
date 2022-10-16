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
import org.labkey.api.util.PageFlowUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 12:39 PM
 */
public class mGapReleaseAnnotateNovelSitesStep extends AbstractCommandPipelineStep<mGapReleaseAnnotateNovelSitesStep.AnnotateNovelSitesWrapper> implements VariantProcessingStep
{
    public static final String REF_VCF = "refVcf";

    public mGapReleaseAnnotateNovelSitesStep(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx, new AnnotateNovelSitesWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<mGapReleaseAnnotateNovelSitesStep> implements SupportsScatterGather
    {
        public Provider()
        {
            super("mGapAnnotateNovelSites", "mGapReleaseComparison", "AnnotateNovelSites", "Compare the VCF to the specified mGAP release VCF, producing TSV/VCF reports with site- and genotype-level concordance.", Arrays.asList(
                    ToolParameterDescriptor.createExpDataParam(REF_VCF, "mGAP Release", "The mGAP release VCF to use for comparison", "sequenceanalysis-sequenceoutputfileselectorfield", new JSONObject(){{
                        put("allowBlank", false);
                        put("category", "mGAP Release");
                        put("performGenomeFilter", false);
                    }}, null),
                    ToolParameterDescriptor.create("releaseVersion", "Version", "This string will be used as the version when published.", "textfield", new JSONObject(){{
                        put("allowBlank", false);
                    }}, null)
            ), PageFlowUtil.set("sequenceanalysis/field/SequenceOutputFileSelectorField.js"), null);
        }

        @Override
        public mGapReleaseAnnotateNovelSitesStep create(PipelineContext ctx)
        {
            return new mGapReleaseAnnotateNovelSitesStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();
        getPipelineCtx().getLogger().info("Annotating VCF by mGAP Release");

        Integer refFileId = getProvider().getParameterByName(REF_VCF).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class);
        File refVcf = getPipelineCtx().getSequenceSupport().getCachedData(refFileId);
        if (refVcf == null || !refVcf.exists())
        {
            throw new PipelineJobException("Unable to find file: " + refFileId + "/" + refVcf);
        }

        String releaseVersion = getProvider().getParameterByName("releaseVersion").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class, "0.0");

        List<String> extraArgs = new ArrayList<>();
        if (intervals != null)
        {
            intervals.forEach(interval -> {
                extraArgs.add("-L");
                extraArgs.add(interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd());
            });
        }

        File annotatedVCF = new File(outputDirectory, SequenceAnalysisService.get().getUnzippedBaseName(inputVCF.getName()) + ".comparison.vcf.gz");
        getWrapper().execute(inputVCF, refVcf, releaseVersion, annotatedVCF, extraArgs);
        if (!annotatedVCF.exists())
        {
            throw new PipelineJobException("Unable to find output: " + annotatedVCF.getPath());
        }

        output.addInput(inputVCF, "Input VCF");
        output.addInput(refVcf, "Reference VCF");

        output.addOutput(annotatedVCF, "VCF Annotated by mGAP Version");
        output.setVcf(annotatedVCF);

        return output;
    }

    public static class AnnotateNovelSitesWrapper extends AbstractDiscvrSeqWrapper
    {
        public AnnotateNovelSitesWrapper(Logger log)
        {
            super(log);
        }

        public File execute(File vcf, File referenceVcf, String versionString, File vcfOutput, List<String> extraArgs) throws PipelineJobException
        {
            List<String> args = new ArrayList<>(getBaseArgs());
            args.add("AnnotateNovelSites");
            args.add("-V");
            args.add(vcf.getPath());
            args.add("-rv");
            args.add(referenceVcf.getPath());

            args.add("-an");
            args.add("mGAPV");
            args.add("-ad");
            args.add("The first mGAP version where variants at this site appeared");
            args.add("-av");
            args.add(versionString);

            args.add("-O");
            args.add(vcfOutput.getPath());

            if (extraArgs != null)
            {
                args.addAll(extraArgs);
            }

            execute(args);

            return vcfOutput;
        }
    }
}
