package org.labkey.tcrdb.pipeline;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.CellHashingService;
import org.labkey.api.util.FileType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.tcrdb.TCRdbModule;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public class CellRangerVDJCellHashingHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private FileType _fileType = new FileType("vloupe", false);
    public static final String CATEGORY = "Cell Hashing Calls (VDJ)";

    public static final String TARGET_ASSAY = "targetAssay";
    public static final String DELETE_EXISTING_ASSAY_DATA = "deleteExistingAssayData";

    public CellRangerVDJCellHashingHandler()
    {
        super(ModuleLoader.getInstance().getModule(TCRdbModule.class), "CellRanger VDJ Import", "This will either directly import data (if cell hashing is not used), or run CiteSeqCount/MultiSeqClassifier to generate a sample-to-cellbarcode TSV based on the filtered barcodes from CellRanger VDJ and then import.", new LinkedHashSet<>(PageFlowUtil.set("tcrdb/field/AssaySelectorField.js")), getDefaultParams());
    }

    private static List<ToolParameterDescriptor> getDefaultParams()
    {
        List<ToolParameterDescriptor> ret = new ArrayList<>(Arrays.asList(
                ToolParameterDescriptor.create(TARGET_ASSAY, "Target Assay", "Results will be loaded into this assay.  If no assay is selected, a table will be created with nothing in the DB.", "tcr-assayselectorfield", null, null),
                ToolParameterDescriptor.create(DELETE_EXISTING_ASSAY_DATA, "Delete Any Existing Assay Data", "If selected, prior to importing assay data, and existing assay runs in the target container from this readset will be deleted.", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, true),
                ToolParameterDescriptor.create("useOutputFileContainer", "Submit to Source File Workbook", "If checked, each job will be submitted to the same workbook as the input file, as opposed to submitting all jobs to the same workbook.  This is primarily useful if submitting a large batch of files to process separately. This only applies if 'Run Separately' is selected.", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, false)
        ));

        ret.addAll(CellHashingService.get().getDefaultHashingParams(true));

        return ret;
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && _fileType.isType(o.getFile());
    }

    @Override
    public boolean doRunRemote()
    {
        return true;
    }

    @Override
    public boolean doRunLocal()
    {
        return true;
    }

    @Override
    public SequenceOutputProcessor getProcessor()
    {
        return new CellRangerVDJCellHashingHandler.Processor();
    }

    @Override
    public boolean doSplitJobs()
    {
        return true;
    }

    @Override
    public boolean requiresSingleGenome()
    {
        return false;
    }

    public class Processor implements SequenceOutputHandler.SequenceOutputProcessor
    {
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            //NOTE: this is the pathway to import assay data, whether hashing is used or not
            CellHashingService.get().prepareHashingAndCiteSeqFilesIfNeeded(outputDir, job, support, "tcrReadsetId", params.optBoolean("excludeFailedcDNA", true), false, false);
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void complete(PipelineJob job, List<SequenceOutputFile> inputFiles, List<SequenceOutputFile> outputsCreated, SequenceAnalysisJobSupport support) throws PipelineJobException
        {
            for (SequenceOutputFile so : outputsCreated)
            {
                if (CATEGORY.equals(so.getCategory()))
                {
                    CellHashingService.get().processMetrics(so, job, true);
                }
            }

            if (StringUtils.trimToNull(job.getParameters().get(TARGET_ASSAY)) == null)
            {
                job.getLogger().info("No assay selected, will not import");
            }
            else
            {
                Integer assayId = ConvertHelper.convert(job.getParameters().get(TARGET_ASSAY), Integer.class);
                if (assayId == null)
                {
                    throw new PipelineJobException("Invalid assay Id, cannot import: " + job.getParameters().get(TARGET_ASSAY));
                }

                Boolean deleteExistingData = false;
                if (job.getParameters().get(DELETE_EXISTING_ASSAY_DATA) != null)
                {
                    deleteExistingData = ConvertHelper.convert(job.getParameters().get(DELETE_EXISTING_ASSAY_DATA), Boolean.class);
                }

                for (SequenceOutputFile so : inputFiles)
                {
                    AnalysisModel model = support.getCachedAnalysis(so.getAnalysis_id());
                    new CellRangerVDJUtils(job.getLogger()).importAssayData(job, model, so.getFile().getParentFile(), assayId, null, deleteExistingData);
                }
            }
        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            RecordedAction action = new RecordedAction(getName());
            for (SequenceOutputFile so : inputFiles)
            {
                ctx.getLogger().info("processing file: " + so.getName());

                //find TSV:
                File perCellTsv = CellRangerVDJUtils.getPerCellCsv(so.getFile().getParentFile());
                if (!perCellTsv.exists())
                {
                    throw new PipelineJobException("Unable to find file: " + perCellTsv.getPath());
                }

                Readset rs = ctx.getSequenceSupport().getCachedReadset(so.getReadset());
                if (rs == null)
                {
                    throw new PipelineJobException("Unable to find readset for outputfile: " + so.getRowid());
                }
                else if (rs.getReadsetId() == null)
                {
                    throw new PipelineJobException("Readset lacks a rowId for outputfile: " + so.getRowid());
                }

                processVloupeFile(ctx, perCellTsv, rs, action, so.getLibrary_id());
            }

            ctx.addActions(action);
        }

        private void processVloupeFile(JobContext ctx, File perCellTsv, Readset rs, RecordedAction action, Integer genomeId) throws PipelineJobException
        {
            List<String> extraParams = new ArrayList<>();
            extraParams.addAll(getClientCommandArgs(ctx.getParams()));

            //prepare whitelist of cell indexes
            AlignmentOutputImpl output = new AlignmentOutputImpl();
            boolean scanEditDistances = ctx.getParams().optBoolean("scanEditDistances", false);
            boolean useSeurat = ctx.getParams().optBoolean("useSeurat", true);
            boolean useMultiSeq = ctx.getParams().optBoolean("useMultiSeq", true);
            int minCountPerCell = ctx.getParams().optInt("minCountPerCell", 3);
            int editDistance = ctx.getParams().optInt("editDistance", 2);

            File cellToHto = CellHashingService.get().runRemoteVdjCellHashingTasks(output, CATEGORY, perCellTsv, rs, ctx.getSequenceSupport(), extraParams, ctx.getWorkingDirectory(), ctx.getSourceDirectory(), editDistance, scanEditDistances, genomeId, minCountPerCell, useSeurat, useMultiSeq);
            if (CellHashingService.get().usesCellHashing(ctx.getSequenceSupport(), ctx.getSourceDirectory()) && cellToHto == null)
            {
                throw new PipelineJobException("Missing cell to HTO file");

            }

            ctx.getFileManager().addStepOutputs(action, output);

        }
    }
}