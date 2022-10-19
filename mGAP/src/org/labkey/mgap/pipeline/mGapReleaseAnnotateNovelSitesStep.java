package org.labkey.mgap.pipeline;

import htsjdk.samtools.util.Interval;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractDiscvrSeqWrapper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.mgap.mGAPSchema;

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
    public static final String PRIOR_RELEASE_LABEL = "priorReleaseLabel";

    public mGapReleaseAnnotateNovelSitesStep(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx, new AnnotateNovelSitesWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<mGapReleaseAnnotateNovelSitesStep> implements SupportsScatterGather
    {
        public Provider()
        {
            super("mGapAnnotateNovelSites", "Annotate Novel Sites Against mGAP Release", "AnnotateNovelSites", "Compare the VCF to the specified mGAP release VCF, producing TSV/VCF reports with site- and genotype-level concordance.", Arrays.asList(
                    ToolParameterDescriptor.createExpDataParam(REF_VCF, "mGAP Release", "The mGAP release VCF to use for comparison", "sequenceanalysis-sequenceoutputfileselectorfield", new JSONObject(){{
                        put("allowBlank", false);
                        put("category", "mGAP Release");
                        put("performGenomeFilter", false);
                        put("doNotIncludeInTemplates", true);
                    }}, null),
                    ToolParameterDescriptor.create("releaseVersion", "mGAP Version", "This string will be used to tag novel variants.", "textfield", new JSONObject(){{
                        put("allowBlank", false);
                        put("doNotIncludeInTemplates", true);
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
        String priorReleaseLabel = getPipelineCtx().getSequenceSupport().getCachedObject(PRIOR_RELEASE_LABEL, String.class);

        List<String> extraArgs = new ArrayList<>();
        if (intervals != null)
        {
            intervals.forEach(interval -> {
                extraArgs.add("-L");
                extraArgs.add(interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd());
            });

            extraArgs.add("--ignore-variants-starting-outside-interval");
        }

        extraArgs.add("-dv");
        extraArgs.add(priorReleaseLabel);

        File annotatedVCF = new File(outputDirectory, SequenceAnalysisService.get().getUnzippedBaseName(inputVCF.getName()) + ".comparison.vcf.gz");
        getWrapper().execute(inputVCF, refVcf, genome.getWorkingFastaFile(), releaseVersion, annotatedVCF, extraArgs);
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

    @Override
    public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles) throws PipelineJobException
    {
        Integer refFileId = getProvider().getParameterByName(REF_VCF).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class);
        String version = new TableSelector(mGAPSchema.getInstance().getSchema().getTable(mGAPSchema.TABLE_VARIANT_CATALOG_RELEASES), PageFlowUtil.set("name"), new SimpleFilter(FieldKey.fromString("dataid"), refFileId), null).getObject(String.class);
        if (version == null)
        {
            throw new PipelineJobException("Unable to find release for fileId: " + refFileId);
        }

        version = version.split(": ")[1];

        support.cacheObject(PRIOR_RELEASE_LABEL, version);
    }

    public static class AnnotateNovelSitesWrapper extends AbstractDiscvrSeqWrapper
    {
        public AnnotateNovelSitesWrapper(Logger log)
        {
            super(log);
        }

        public File execute(File vcf, File referenceVcf, File fasta, String versionString, File vcfOutput, List<String> extraArgs) throws PipelineJobException
        {
            List<String> args = new ArrayList<>(getBaseArgs());
            args.add("AnnotateNovelSites");
            args.add("-R");
            args.add(fasta.getPath());

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
