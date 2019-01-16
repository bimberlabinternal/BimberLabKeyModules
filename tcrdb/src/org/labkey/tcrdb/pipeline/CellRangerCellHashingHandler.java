package org.labkey.tcrdb.pipeline;

import au.com.bytecode.opencsv.CSVReader;
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
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.tcrdb.TCRdbModule;
import org.labkey.tcrdb.TCRdbSchema;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class CellRangerCellHashingHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private FileType _fileType = new FileType("cloupe", false);
    private static final String READSET_TO_HASHING_MAP = "readsetToHashingMap";

    public CellRangerCellHashingHandler()
    {
        super(ModuleLoader.getInstance().getModule(TCRdbModule.class), "CellRanger GEX/Cell Hashing", "This will run CiteSeqCount/MultiSeqClassifier to generate a sample-to-cellbarcode TSV based on the filtered barcodes from CellRanger.", new LinkedHashSet<>(PageFlowUtil.set("sequenceanalysis/field/CellRangerAggrTextarea.js")), Arrays.asList(

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
        return false;
    }

    @Override
    public SequenceOutputProcessor getProcessor()
    {
        return new CellRangerCellHashingHandler.Processor();
    }

    @Override
    public boolean doSplitJobs()
    {
        return true;
    }

    public class Processor implements SequenceOutputHandler.SequenceOutputProcessor
    {
        private String getFilterFieldName()
        {
            return "readsetId";
        }

        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            job.getLogger().debug("preparing cell hashing files");
            Container target = job.getContainer().isWorkbook() ? job.getContainer().getParent() : job.getContainer();
            UserSchema tcr = QueryService.get().getUserSchema(job.getUser(), target, TCRdbSchema.NAME);
            TableInfo cDNAs = tcr.getTable(TCRdbSchema.TABLE_CDNAS);

            Map<FieldKey, ColumnInfo> colMap = QueryService.get().getColumns(cDNAs, PageFlowUtil.set(
                    FieldKey.fromString("rowid"),
                    FieldKey.fromString("sortId/hto"),
                    FieldKey.fromString("sortId/hto/sequence"),
                    FieldKey.fromString("hashingReadsetId"))
            );

            File barcodeOutput = getValidHashingBarcodeFile(outputDir);
            HashMap<Integer, Integer> readsetToHashingMap = new HashMap<>();
            try (CSVWriter bcWriter = new CSVWriter(PrintWriters.getPrintWriter(barcodeOutput), ',', CSVWriter.NO_QUOTE_CHARACTER))
            {
                List<Readset> cachedReadsets = support.getCachedReadsets();
                Set<String> distinctHTOs = new HashSet<>();
                for (Readset rs : cachedReadsets)
                {
                    AtomicBoolean hasError = new AtomicBoolean(false);
                    new TableSelector(cDNAs, colMap.values(), new SimpleFilter(FieldKey.fromString(getFilterFieldName()), rs.getRowId()), null).forEachResults(results -> {

                        if (results.getObject(FieldKey.fromString("hashingReadsetId")) == null)
                        {
                            hasError.set(true);
                        }

                        if (results.getObject(FieldKey.fromString("sortId/hto/sequence")) == null)
                        {
                            hasError.set(true);
                        }

                        support.cacheReadset(results.getInt(FieldKey.fromString("hashingReadsetId")), job.getUser());
                        readsetToHashingMap.put(rs.getReadsetId(), results.getInt(FieldKey.fromString("hashingReadsetId")));

                        String hto = results.getString(FieldKey.fromString("sortId/hto")) + "<>" + results.getString(FieldKey.fromString("sortId/hto/sequence"));
                        if (!distinctHTOs.contains(hto) && !StringUtils.isEmpty(results.getString(FieldKey.fromString("sortId/hto/sequence"))))
                        {
                            distinctHTOs.add(hto);
                            bcWriter.writeNext(new String[]{results.getString(FieldKey.fromString("sortId/hto/sequence")), results.getString(FieldKey.fromString("sortId/hto"))});
                        }
                    });

                    if (hasError.get())
                    {
                        throw new PipelineJobException("No cell hashing readset or HTO found for one or more cDNAs. see the file");
                    }
                }

                if (distinctHTOs.isEmpty())
                {
                    throw new PipelineJobException("Cell hashing was selected, but no HTOs were found");
                }

                job.getLogger().info("distinct HTOs: " + distinctHTOs.size());

                support.cacheObject(READSET_TO_HASHING_MAP, readsetToHashingMap);
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
                //find TSV:
                File barcodeDir = new File(so.getFile().getParentFile(), "filtered_gene_bc_matrices");
                File[] children = barcodeDir.listFiles(new FileFilter()
                {
                    @Override
                    public boolean accept(File pathname)
                    {
                        return pathname.isDirectory();
                    }
                });

                if (children == null || children.length != 1)
                {
                    throw new PipelineJobException("Expected to find a single subfolder under: " + barcodeDir.getPath());
                }

                File perCellTsv = new File(children[0], "barcodes.tsv");
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

                processLoupeFile(ctx, perCellTsv, rs, so.getLibrary_id(), action);
            }

            ctx.addActions(action);
        }

        private File processLoupeFile(SequenceOutputHandler.JobContext ctx, File perCellTsv, Readset rs, int genomeId, RecordedAction action) throws PipelineJobException
        {
            Map<Integer, Integer> readsetToHashing = CellRangerVDJWrapper.getCachedReadsetMap(ctx.getSequenceSupport());
            ctx.getLogger().debug("total cashed readset/HTO pairs: " + readsetToHashing.size());

            //prepare whitelist of cell indexes
            File cellBarcodeWhitelist = getValidCellIndexFile(ctx.getSourceDirectory());
            Set<String> uniqueBarcodes = new HashSet<>();
            ctx.getLogger().debug("writing cell barcodes");
            try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(cellBarcodeWhitelist), ',', CSVWriter.NO_QUOTE_CHARACTER);CSVReader reader = new CSVReader(Readers.getReader(perCellTsv), '\t'))
            {
                int rowIdx = 0;
                String[] row;
                while ((row = reader.readNext()) != null)
                {
                    //skip header
                    rowIdx++;
                    if (rowIdx > 1)
                    {
                        //NOTE: 10x appends "-1" to barcodes
                        String barcode = row[0].split("-")[0];
                        if (!uniqueBarcodes.contains(barcode))
                        {
                            writer.writeNext(new String[]{barcode});
                            uniqueBarcodes.add(barcode);
                        }
                    }
                }

                ctx.getLogger().debug("rows inspected: " + (rowIdx - 1));
                ctx.getLogger().debug("unique cell barcodes: " + uniqueBarcodes.size());
                ctx.getFileManager().addIntermediateFile(cellBarcodeWhitelist);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            //prepare whitelist of barcodes, based on cDNA records
            File htoBarcodeWhitelist = getValidHashingBarcodeFile(ctx.getSourceDirectory());
            if (!htoBarcodeWhitelist.exists())
            {
                throw new PipelineJobException("Unable to find file: " + htoBarcodeWhitelist.getPath());
            }
            ctx.getFileManager().addIntermediateFile(htoBarcodeWhitelist);

            Readset htoReadset = ctx.getSequenceSupport().getCachedReadset(readsetToHashing.get(rs.getReadsetId()));
            if (htoReadset == null)
            {
                throw new PipelineJobException("Unable to find HTO readset for readset: " + rs.getReadsetId());
            }

            //run CiteSeqCount.  this will use Multiseq to make calls per cell
            File cellToHto = getCellToHtoFile(ctx.getSourceDirectory());
            File citeSeqCountUnknownOutput = new File(cellToHto.getParentFile(), "citeSeqUnknownBarcodes.txt");

            List<String> extraParams = new ArrayList<>();
            extraParams.add("-u");
            extraParams.add(citeSeqCountUnknownOutput.getPath());

            SequencePipelineService.get().runCiteSeqCount(htoReadset, htoBarcodeWhitelist, cellBarcodeWhitelist, cellToHto.getParentFile(), FileUtil.getBaseName(cellToHto.getName()), ctx.getLogger(), extraParams);
            ctx.getFileManager().addOutput(action, "CiteSeqCount Counts", cellToHto);
            ctx.getFileManager().addOutput(action,"CiteSeqCount Unknown Barcodes", citeSeqCountUnknownOutput);

            ctx.getFileManager().addSequenceOutput(cellToHto, rs.getName() + ": Cell Hashing Calls", "10x GEX Cell Hashing Calls", rs.getReadsetId(), null, genomeId, null);

            if (citeSeqCountUnknownOutput.exists())
            {
                CellRangerVDJWrapper.logTopUnknownBarcodes(citeSeqCountUnknownOutput, ctx.getLogger());
            }

            return cellToHto;
        }

        private File getValidHashingBarcodeFile(File webserverDir)
        {
            return new File(webserverDir, "validHashingBarcodes.csv");
        }

        private File getValidCellIndexFile(File webserverDir)
        {
            return new File(webserverDir, "validCellIndexes.csv");
        }

        private File getCellToHtoFile(File webserverDir)
        {
            return new File(webserverDir, "cellbarcodeToHTO.txt");
        }
    }
}