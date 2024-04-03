package org.labkey.mgap.pipeline;

import au.com.bytecode.opencsv.CSVReader;
import htsjdk.samtools.util.Interval;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.TableInfo;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PedigreeToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.api.sequenceanalysis.run.AbstractDiscvrSeqWrapper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.mgap.mGAPSchema;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class GeographicOriginStep extends AbstractPipelineStep implements VariantProcessingStep
{
    public static final String CATEGORY = "Macaque Geographic Origin Scores";

    public GeographicOriginStep(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    private static final String INDIAN_MARKERS = "indianMarkers";
    private static final String CHINESE_MARKERS = "chineseMarkers";

    public static class Provider extends AbstractVariantProcessingStepProvider<GeographicOriginStep> implements RequiresPedigree
    {
        public Provider()
        {
            super("GeographicOriginStep", "Geographic Origin", "", "This will run VariantConcordanceScore to score samples in the input VCF against reference VCFs with marker alleles for Indian-origin and Chinese-origin rhesus macaques", Arrays.asList(
                    ToolParameterDescriptor.createExpDataParam(INDIAN_MARKERS, "Indian-origin Markers", "This is the ID of a VCF file with markers of indian-origin.", "ldk-expdatafield", new JSONObject()
                    {{
                        put("allowBlank", false);
                    }}, null),
                    ToolParameterDescriptor.createExpDataParam(CHINESE_MARKERS, "Chinese-origin Markers", "This is the ID of a VCF file with markers of chinese-origin.", "ldk-expdatafield", new JSONObject()
                    {{
                        put("allowBlank", false);
                    }}, null),
                    ToolParameterDescriptor.create("storeResults", "Store Results", "If checked, the results will be stored in the database", "checkbox", null, null),
                    new PedigreeToolParameterDescriptor()
            ), PageFlowUtil.set(PedigreeToolParameterDescriptor.getClientDependencyPath()), "https://bimberlab.github.io/DISCVRSeq/");
        }

        @Override
        public GeographicOriginStep create(PipelineContext ctx)
        {
            return new GeographicOriginStep(this, ctx);
        }
    }

    @Override
    public void complete(PipelineJob job, List<SequenceOutputFile> inputs, List<SequenceOutputFile> outputsCreated, SequenceAnalysisJobSupport support) throws PipelineJobException
    {
        // TODO: store in DB
        for (SequenceOutputFile so : outputsCreated)
        {
            if (!CATEGORY.equals(so.getCategory()))
            {
                continue;
            }

            List<Map<String, Object>> toInsert = new ArrayList<>();
            try (CSVReader reader = new CSVReader(Readers.getReader(so.getFile()), '\t'))
            {
                String[] line;
                while ((line = reader.readNext()) != null)
                {
                    // SampleName	ReferenceName	MarkersWithData	MarkersNoData	FractionWithData	UniqueContigs	CumulativeAF	MinPossible	MaxPossible	Score
                    Map<String, Object> row = new CaseInsensitiveHashMap<>();
                    row.put("sampleName", line[0]);
                    row.put("measurementName", line[1]);
                    row.put("measurementValue", line[9]);
                    row.put("dataId", so.getDataId());

                    toInsert.add(row);
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            if (!toInsert.isEmpty())
            {
                TableInfo ti = QueryService.get().getUserSchema(job.getUser(), job.getContainer(), mGAPSchema.NAME).getTable(mGAPSchema.TABLE_GENETIC_MEASUREMENTS);
                BatchValidationException bve = new BatchValidationException();
                try
                {
                    ti.getUpdateService().insertRows(job.getUser(), job.getContainer(), toInsert, bve, null, null);
                }
                catch (DuplicateKeyException | BatchValidationException | QueryUpdateServiceException | SQLException e)
                {
                    throw new PipelineJobException(e);
                }
            }
        }
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();

        List<String> options = new ArrayList<>();

        Integer indianVcf = getProvider().getParameterByName(INDIAN_MARKERS).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class);
        File indianVcfFile = getPipelineCtx().getSequenceSupport().getCachedData(indianVcf);
        if (!indianVcfFile.exists())
        {
            throw new PipelineJobException("Unable to find file: " + indianVcfFile.getPath());
        }
        options.add("--ref-sites:INDIAN");
        options.add(indianVcfFile.getPath());
        output.addInput(indianVcfFile, "Markers VCF");

        Integer chineseVcf = getProvider().getParameterByName(CHINESE_MARKERS).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class);
        File chineseVcfFile = getPipelineCtx().getSequenceSupport().getCachedData(chineseVcf);
        if (!chineseVcfFile.exists())
        {
            throw new PipelineJobException("Unable to find file: " + chineseVcfFile.getPath());
        }
        options.add("--ref-sites:CHINESE");
        options.add(chineseVcfFile.getPath());
        output.addInput(chineseVcfFile, "Markers VCF");

        if (intervals != null)
        {
            intervals.forEach(interval -> {
                options.add("-L");
                options.add(interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd());
            });
        }

        File outputTable = new File(outputDirectory, SequencePipelineService.get().getUnzippedBaseName(inputVCF.getName()) + ".origin.txt");
        VariantConcordanceScoreWrapper wrapper = new VariantConcordanceScoreWrapper(getPipelineCtx().getLogger());
        wrapper.execute(inputVCF, genome.getWorkingFastaFile(), outputTable, options);

        output.addInput(inputVCF, "Input VCF");
        output.addInput(genome.getWorkingFastaFile(), "Reference Genome");
        output.addOutput(outputTable, "Macaque Geographic Origin Scores");

        output.addSequenceOutput(outputTable, "Macaque Geographic Origin Scores for: " + inputVCF.getName(), CATEGORY, null, null, genome.getGenomeId(), null);

        return output;
    }

    public static class VariantConcordanceScoreWrapper extends AbstractDiscvrSeqWrapper
    {
        public VariantConcordanceScoreWrapper(Logger log)
        {
            super(log);
        }

        public File execute(File inputVCF, File referenceFasta, File outputTable, List<String> options) throws PipelineJobException
        {
            List<String> args = new ArrayList<>(getBaseArgs());
            args.add("VariantConcordanceScore");
            args.add("-R");
            args.add(referenceFasta.getPath());
            args.add("-V");
            args.add(inputVCF.getPath());
            args.add("-O");
            args.add(outputTable.getPath());
            if (options != null)
            {
                args.addAll(options);
            }

            execute(args);
            if (!outputTable.exists())
            {
                throw new PipelineJobException("Expected output not found: " + outputTable.getPath());
            }

            return outputTable;
        }
    }
}
