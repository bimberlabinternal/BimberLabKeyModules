package org.labkey.tcrdb.pipeline;

import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.tcrdb.TCRdbModule;
import org.labkey.tcrdb.TCRdbSchema;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SeuratCiteSeqHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    protected FileType _fileType = new FileType(".seurat.rds", false);
    public static final String CATEGORY = "Seurat CITE-Seq Count Matrix";

    public SeuratCiteSeqHandler()
    {
        super(ModuleLoader.getInstance().getModule(TCRdbModule.class), "Seurat GEX/CITE-seq Counts", "This will run CiteSeqCount to generate a sample-to-cellbarcode TSV based on the cell barcodes present in the saved Seurat object.", null, Arrays.asList(
                ToolParameterDescriptor.create("editDistance", "Edit Distance", null, "ldk-integerfield", null, 3),
                ToolParameterDescriptor.create("excludeFailedcDNA", "Exclude Failed cDNA", "If selected, cDNAs with non-blank status fields will be omitted", "checkbox", null, true),
                ToolParameterDescriptor.create("minCountPerCell", "Min Reads/Cell (Cell Hashing)", null, "ldk-integerfield", null, 5),
                ToolParameterDescriptor.create("useOutputFileContainer", "Submit to Source File Workbook", "If checked, each job will be submitted to the same workbook as the input file, as opposed to submitting all jobs to the same workbook.  This is primarily useful if submitting a large batch of files to process separately..", "checkbox", new JSONObject()
                {{
                    put("checked", true);
                }}, false)
        ));
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
        return false;
    }

    @Override
    public SequenceOutputProcessor getProcessor()
    {
        return new Processor();
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
            new CellRangerVDJUtils(job.getLogger(), outputDir).prepareHashingAndCiteSeqFilesIfNeeded(job, support,"readsetId", params.optBoolean("excludeFailedcDNA", true), false, true);
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, SequenceOutputHandler.JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            RecordedAction action = new RecordedAction(getName());

            Map<Integer, Integer> readsetToCiteSeq = CellRangerVDJUtils.getCachedCiteSeqReadsetMap(ctx.getSequenceSupport());
            ctx.getLogger().debug("total cached readset to GEX/citeseq pairs: " + readsetToCiteSeq.size());

            for (SequenceOutputFile so : inputFiles)
            {
                ctx.getLogger().info("processing file: " + so.getName());

                File barcodes = SeuratCellHashingHandler.getBarcodesFromSeurat(so.getFile());

                Readset rs = ctx.getSequenceSupport().getCachedReadset(so.getReadset());
                if (rs == null)
                {
                    throw new PipelineJobException("Unable to find readset for outputfile: " + so.getRowid());
                }
                else if (rs.getReadsetId() == null)
                {
                    throw new PipelineJobException("Readset lacks a rowId for outputfile: " + so.getRowid());
                }

                Readset citeseqReadset = ctx.getSequenceSupport().getCachedReadset(readsetToCiteSeq.get(rs.getReadsetId()));
                if (citeseqReadset == null)
                {
                    throw new PipelineJobException("Unable to find Cite-seq readset for GEX readset: " + rs.getReadsetId());
                }

                File adtWhitelist = CellRangerVDJUtils.getValidCiteSeqBarcodeFile(ctx.getOutputDir(), so.getReadset());
                File citeSeqMatrix = CellRangerCellHashingHandler.processBarcodeFile(ctx, barcodes, rs, citeseqReadset, so.getLibrary_id(), action, getClientCommandArgs(ctx.getParams()), false, CATEGORY, true, adtWhitelist, false);
                if (!citeSeqMatrix.exists())
                {
                    throw new PipelineJobException("Unable to find expected file: " + citeSeqMatrix.getPath());
                }
            }

            ctx.addActions(action);
        }
    }
}
