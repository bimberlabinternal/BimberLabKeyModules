package org.labkey.tcrdb.pipeline;

import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class SeuratCiteSeqHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    protected FileType _fileType = new FileType(".seurat.rds", false);
    public static final String CATEGORY = "Seurat CITE-Seq Count Matrix";
    private static final String DEFAULT_TAG_GROUP = "TotalSeq-C";

    public SeuratCiteSeqHandler()
    {
        super(ModuleLoader.getInstance().getModule(TCRdbModule.class), "Seurat GEX/CITE-seq Counts", "This will run CiteSeqCount to generate a sample-to-cellbarcode TSV based on the cell barcodes present in the saved Seurat object.", null, Arrays.asList(
                ToolParameterDescriptor.create("editDistance", "Edit Distance", null, "ldk-integerfield", null, 3),
                ToolParameterDescriptor.create("excludeFailedcDNA", "Exclude Failed cDNA", "If selected, cDNAs with non-blank status fields will be omitted", "checkbox", null, true),
                ToolParameterDescriptor.create("minCountPerCell", "Min Reads/Cell (Cell Hashing)", null, "ldk-integerfield", null, 5),
                ToolParameterDescriptor.create("tagGroup", "Tag List", null, "ldk-simplelabkeycombo", new JSONObject(){{
                    put("schemaName", "sequenceanalysis");
                    put("queryName", "barcode_groups");
                    put("displayField", "group_name");
                    put("valueField", "group_name");
                    put("allowBlank", false);
                }}, DEFAULT_TAG_GROUP),
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
            Container target = job.getContainer().isWorkbook() ? job.getContainer().getParent() : job.getContainer();
            UserSchema tcr = QueryService.get().getUserSchema(job.getUser(), target, TCRdbSchema.NAME);
            TableInfo cDNAs = tcr.getTable(TCRdbSchema.TABLE_CDNAS, null);

            job.getLogger().debug("preparing cDNA and CITE-seq files");

            Map<FieldKey, ColumnInfo> colMap = QueryService.get().getColumns(cDNAs, PageFlowUtil.set(
                    FieldKey.fromString("rowid"),
                    FieldKey.fromString("sortId/stimId/animalId"),
                    FieldKey.fromString("sortId/stimId/stim"),
                    FieldKey.fromString("sortId/population"),
                    FieldKey.fromString("citeseqReadsetId"),
                    FieldKey.fromString("citeseqReadsetId/totalFiles"),
                    FieldKey.fromString("status"))
            );

            File barcodeOutput = CellRangerVDJUtils.getValidHashingBarcodeFile(outputDir);
            String tagGroup = StringUtils.trimToNull(params.getString("tagGroup"));
            if (tagGroup == null)
            {
                throw new PipelineJobException("No barcode group supplied");
            }

            SequenceAnalysisService.get().writeAllBarcodes(barcodeOutput, job.getUser(), job.getContainer(), tagGroup);

            long barcodeCount = SequencePipelineService.get().getLineCount(barcodeOutput);
            if (barcodeCount == 0)
            {
                throw new PipelineJobException("No barcodes found for group: " + tagGroup);
            }
            job.getLogger().info("Total CITE-seq barcodes written: " + barcodeCount);

            HashMap<Integer, Integer> readsetToCiteSeqMap = new HashMap<>();
            File output = CellRangerVDJUtils.getCDNAInfoFile(outputDir);
            try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(output), '\t', CSVWriter.NO_QUOTE_CHARACTER))
            {
                writer.writeNext(new String[]{"ReadsetId", "CDNA_ID", "AnimalId", "Stim", "Population", "CiteSeqReadsetId", "HasCiteSeqReads"});
                List<Readset> cachedReadsets = support.getCachedReadsets();
                for (Readset rs : cachedReadsets)
                {
                    AtomicBoolean hasError = new AtomicBoolean(false);
                    //find cDNA records using this readset
                    new TableSelector(cDNAs, colMap.values(), new SimpleFilter(FieldKey.fromString("readsetid"), rs.getRowId()), null).forEachResults(results -> {
                        writer.writeNext(new String[]{
                                String.valueOf(rs.getRowId()),
                                results.getString(FieldKey.fromString("rowid")),
                                results.getString(FieldKey.fromString("sortId/stimId/animalId")),
                                results.getString(FieldKey.fromString("sortId/stimId/stim")),
                                results.getString(FieldKey.fromString("sortId/population")),
                                String.valueOf(results.getObject(FieldKey.fromString("citeseqReadsetId")) == null ? "" : results.getInt(FieldKey.fromString("citeseqReadsetId"))),
                                String.valueOf(results.getObject(FieldKey.fromString("citeseqReadsetId/totalFiles")) != null && results.getInt(FieldKey.fromString("citeseqReadsetId/totalFiles")) > 0)
                        });

                        if (results.getObject(FieldKey.fromString("citeseqReadsetId")) == null)
                        {
                            hasError.set(true);
                            return;
                        }

                        readsetToCiteSeqMap.put(rs.getReadsetId(), results.getInt(FieldKey.fromString("citeseqReadsetId")));
                    });

                    if (hasError.get())
                    {
                        throw new PipelineJobException("No CITE-seq readset found for one or more cDNAs. see the file: " + output.getName());
                    }
                }

                readsetToCiteSeqMap.forEach((readsetId, citeseqReadsetId) -> support.cacheReadset(citeseqReadsetId, job.getUser()));
                support.cacheObject(CellRangerVDJUtils.READSET_TO_HASHING_MAP, readsetToCiteSeqMap);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, SequenceOutputHandler.JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            RecordedAction action = new RecordedAction(getName());

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

                File citeSeqMatrix = CellRangerCellHashingHandler.processBarcodeFile(ctx, barcodes, rs, so.getLibrary_id(), action, getClientCommandArgs(ctx.getParams()), false, CATEGORY, false);
                if (!citeSeqMatrix.exists())
                {
                    throw new PipelineJobException("Unable to find expected file: " + citeSeqMatrix.getPath());
                }

            }

            ctx.addActions(action);
        }
    }
}
