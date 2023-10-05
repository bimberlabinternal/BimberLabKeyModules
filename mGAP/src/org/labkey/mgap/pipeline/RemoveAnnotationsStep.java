package org.labkey.mgap.pipeline;

import htsjdk.samtools.util.Interval;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.util.PageFlowUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RemoveAnnotationsStep extends AbstractCommandPipelineStep<RemoveAnnotationsForMgapStep.RemoveAnnotationsWrapper> implements VariantProcessingStep
{
    public RemoveAnnotationsStep(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx, new RemoveAnnotationsForMgapStep.RemoveAnnotationsWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<RemoveAnnotationsStep> implements SupportsScatterGather
    {
        public Provider()
        {
            super("RemoveAnnotations", "Remove Annotations", "RemoveAnnotations", "This will remove INFO annotations from the selected VCF, based on the arguments below.", List.of(
                    ToolParameterDescriptor.create("annotationToKeep", "Annotations To Keep", "A list of INFO annotations to keep. Others will be discarded", "sequenceanalysis-trimmingtextarea", new JSONObject(){{

                    }}, null),
                    ToolParameterDescriptor.create("annotationToRemove", "Annotations To Remove", "A list of INFO annotations to remove. If --annotationToKeep is provided, this will be ignored", "sequenceanalysis-trimmingtextarea", new JSONObject(){{

                    }}, null),
                    ToolParameterDescriptor.create("genotypeAnnotationToKeep", "Genotype Annotations To Keep", "A list of genotype annotations to keep. Others will be discarded", "sequenceanalysis-trimmingtextarea", new JSONObject(){{

                    }}, null),
                    ToolParameterDescriptor.create("genotypeAnnotationToRemove", "Genotype Annotations To Remove", "A list of genotype annotations to keep. Others will be kept, unless --genotypeAnnotationToKeep is also provided", "sequenceanalysis-trimmingtextarea", new JSONObject(){{

                    }}, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--sitesOnly"), "sitesOnly", "Sites Only Output", "If checked, genotypes will be dropped from the output VCF.", "checkbox", new JSONObject(){{

                    }}, false),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-ef"), "excludeFiltered", "Exclude Filtered", "If checked, filtered sites will be excluded from the output.", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-cgf"), "clearGenotypeFilter", "Clear Genotype Filter", "Clear the filter field on all genotypes.  This executes after setFilteredGTToNoCall.", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true)

            ), PageFlowUtil.set("/sequenceanalysis/field/TrimmingTextArea.js"), null);
        }

        @Override
        public PipelineStep create(PipelineContext context)
        {
            return new RemoveAnnotationsStep(this, context);
        }
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
            appendParam(args, "annotationToKeep");
            appendParam(args, "annotationToRemove");
            appendParam(args, "genotypeAnnotationToKeep");
            appendParam(args, "genotypeAnnotationToRemove");
            args.addAll(getClientCommandArgs());

            getWrapper().execute(inputVCF, outputFile, genome.getWorkingFastaFile(), args, intervals);
        }

        output.setVcf(outputFile);
        output.addIntermediateFile(outputFile);
        output.addIntermediateFile(new File(outputFile.getPath() + ".tbi"));

        return output;
    }

    private void appendParam(List<String> args, String paramName)
    {
        String annotationToKeepVal = StringUtils.trimToNull(getProvider().getParameterByName(paramName).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class));
        if (annotationToKeepVal != null)
        {
            for (String val : annotationToKeepVal.split(";"))
            {
                args.add("--" + paramName);
                args.add(val);
            }
        }
    }

    private boolean indexExists(File vcf)
    {
        return new File(vcf.getPath() + ".tbi").exists();
    }
}
