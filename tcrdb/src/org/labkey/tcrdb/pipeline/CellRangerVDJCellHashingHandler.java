package org.labkey.tcrdb.pipeline;

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
import org.labkey.api.util.FileType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.tcrdb.TCRdbModule;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import static org.labkey.tcrdb.pipeline.CellRangerVDJWrapper.TARGET_ASSAY;

public class CellRangerVDJCellHashingHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private FileType _fileType = new FileType("vloupe", false);

    public CellRangerVDJCellHashingHandler()
    {
        super(ModuleLoader.getInstance().getModule(TCRdbModule.class), "CellRanger VDJ/Cell Hashing", "This will run CiteSeqCount/MultiSeqClassifier to generate a sample-to-cellbarcode TSV based on the filtered barcodes from CellRanger VDJ. Results will be imported into the selected assay.", new LinkedHashSet<>(PageFlowUtil.set("tcrdb/field/AssaySelectorField.js")), Arrays.asList(
                ToolParameterDescriptor.create(TARGET_ASSAY, "Target Assay", "Results will be loaded into this assay.  If no assay is selected, a table will be created with nothing in the DB.", "tcr-assayselectorfield", null, null)
        ));
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && _fileType.isType(o.getFile());
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

    public class Processor implements SequenceOutputHandler.SequenceOutputProcessor
    {
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            CellRangerVDJUtils utils = new CellRangerVDJUtils(job.getLogger(), outputDir);
            utils.prepareVDJHasingFiles(job, support);
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void complete(PipelineJob job, List<SequenceOutputFile> inputFiles, List<SequenceOutputFile> outputsCreated, SequenceAnalysisJobSupport support) throws PipelineJobException
        {

            CellRangerVDJUtils utils = new CellRangerVDJUtils(job.getLogger(), job.getLogFile().getParentFile());
            if (job.getParameters().get(TARGET_ASSAY) == null)
            {
                throw new PipelineJobException("No assay selected, will not import");
            }

            Integer assayId = ConvertHelper.convert(job.getParameters().get(TARGET_ASSAY), Integer.class);
            if (assayId == null)
            {
                throw new PipelineJobException("No assay selected, will not import");
            }

            for (SequenceOutputFile so : inputFiles)
            {
                AnalysisModel model = support.getCachedAnalysis(so.getAnalysis_id());
                utils.importAssayData(job, model, so.getFile().getParentFile(), assayId, true, support);
            }
        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            CellRangerVDJUtils utils = new CellRangerVDJUtils(ctx.getLogger(), ctx.getSourceDirectory());
            RecordedAction action = new RecordedAction(getName());

            for (SequenceOutputFile so : inputFiles)
            {
                ctx.getLogger().info("processing file: " + so.getName());

                //find TSV:
                File perCellTsv = utils.getPerCellCsv(so.getFile().getParentFile());
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

                processVloupeFile(ctx, perCellTsv, rs, action);
            }

            ctx.addActions(action);
        }

        private File processVloupeFile(JobContext ctx, File perCellTsv, Readset rs, RecordedAction action) throws PipelineJobException
        {
            CellRangerVDJUtils utils = new CellRangerVDJUtils(ctx.getLogger(), ctx.getSourceDirectory());

            //prepare whitelist of cell indexes
            AlignmentOutputImpl output = new AlignmentOutputImpl();
            File cellToHto = utils.runRemoteCellHashingTasks(output, perCellTsv, rs, ctx.getSequenceSupport());

            ctx.getFileManager().addStepOutputs(action, output);

            return cellToHto;
        }
    }
}