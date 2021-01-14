package org.labkey.tcrdb.pipeline;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.reader.Readers;
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
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.tcrdb.TCRdbModule;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
            AlignmentOutputImpl output = new AlignmentOutputImpl();

            CellHashingService.CellHashingParameters parameters = CellHashingService.CellHashingParameters.createFromJson(CellHashingService.BARCODE_TYPE.hashing, ctx.getParams(), null, rs);
            parameters.cellBarcodeWhitelistFile = createCellbarcodeWhitelist(ctx, perCellTsv, true);
            parameters.genomeId = genomeId;
            parameters.outputCategory = CATEGORY;
            parameters.basename = FileUtil.makeLegalName(rs.getName());

            File cellToHto = CellHashingService.get().processCellHashingOrCiteSeqForParent(rs, output, ctx, parameters);
            if (CellHashingService.get().usesCellHashing(ctx.getSequenceSupport(), ctx.getSourceDirectory()) && cellToHto == null)
            {
                throw new PipelineJobException("Missing cell to HTO file");

            }

            ctx.getFileManager().addStepOutputs(action, output);
        }

        private File createCellbarcodeWhitelist(JobContext ctx, File perCellTsv, boolean allowCellsLackingCDR3) throws PipelineJobException
        {
            //prepare whitelist of cell indexes based on TCR calls:
            File cellBarcodeWhitelist = new File(ctx.getSourceDirectory(), "validCellIndexes.csv");
            Set<String> uniqueBarcodes = new HashSet<>();
            Set<String> uniqueBarcodesIncludingNoCDR3 = new HashSet<>();
            ctx.getLogger().debug("writing cell barcodes, using file: " + perCellTsv.getPath());
            ctx.getLogger().debug("allow cells lacking CDR3: " + allowCellsLackingCDR3);
            try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(cellBarcodeWhitelist), ',', CSVWriter.NO_QUOTE_CHARACTER); CSVReader reader = new CSVReader(Readers.getReader(perCellTsv), ','))
            {
                int rowIdx = 0;
                int noCallRows = 0;
                int nonCell = 0;
                String[] row;
                while ((row = reader.readNext()) != null)
                {
                    //skip header
                    rowIdx++;
                    if (rowIdx > 1)
                    {
                        if ("False".equalsIgnoreCase(row[1]))
                        {
                            nonCell++;
                            continue;
                        }

                        //NOTE: allow these to pass for cell-hashing under some conditions
                        boolean hasCDR3 = !"None".equals(row[12]);
                        if (!hasCDR3)
                        {
                            noCallRows++;
                        }

                        //NOTE: 10x appends "-1" to barcodes
                        String barcode = row[0].split("-")[0];
                        if (hasCDR3 && !uniqueBarcodes.contains(barcode))
                        {
                            writer.writeNext(new String[]{barcode});
                            uniqueBarcodes.add(barcode);
                        }

                        uniqueBarcodesIncludingNoCDR3.add(barcode);
                    }
                }

                ctx.getLogger().debug("rows inspected: " + (rowIdx - 1));
                ctx.getLogger().debug("rows without CDR3: " + noCallRows);
                ctx.getLogger().debug("rows not called as cells: " + nonCell);
                ctx.getLogger().debug("unique cell barcodes (with CDR3): " + uniqueBarcodes.size());
                ctx.getLogger().debug("unique cell barcodes (including no CDR3): " + uniqueBarcodesIncludingNoCDR3.size());
                ctx.getFileManager().addIntermediateFile(cellBarcodeWhitelist);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            if (uniqueBarcodes.size() < 500 && uniqueBarcodesIncludingNoCDR3.size() > uniqueBarcodes.size())
            {
                ctx.getLogger().info("Total cell barcodes with CDR3s is low, so cell hashing will be performing using an input that includes valid cells that lacked CDR3 data.");
                try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(cellBarcodeWhitelist), ',', CSVWriter.NO_QUOTE_CHARACTER))
                {
                    for (String barcode : uniqueBarcodesIncludingNoCDR3)
                    {
                        writer.writeNext(new String[]{barcode});
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            //TODO: consider looking up GEX data?

            return cellBarcodeWhitelist;
        }
    }
}