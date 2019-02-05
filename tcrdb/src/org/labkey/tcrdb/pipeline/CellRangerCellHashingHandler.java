package org.labkey.tcrdb.pipeline;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.tcrdb.TCRdbModule;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CellRangerCellHashingHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private FileType _fileType = new FileType("cloupe", false);

    public CellRangerCellHashingHandler()
    {
        super(ModuleLoader.getInstance().getModule(TCRdbModule.class), "CellRanger GEX/Cell Hashing", "This will run CiteSeqCount/MultiSeqClassifier to generate a sample-to-cellbarcode TSV based on the filtered barcodes from CellRanger.", new LinkedHashSet<>(PageFlowUtil.set("sequenceanalysis/field/CellRangerAggrTextarea.js")), Arrays.asList(
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-hd"), "hd", "Edit Distance", null, "ldk-integerfield", null, null)
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
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            CellRangerVDJUtils.prepareCellHashingFiles(job, support, outputDir, "readsetId");
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

                //find TSV:
                File perCellTsv;
                File barcodeDir = new File(so.getFile().getParentFile(), "filtered_gene_bc_matrices");
                if (!barcodeDir.exists())
                {
                    //this might be a re-analysis loupe directory.  in this case, use the tsne projection.csv as the whitelist:
                    File dir = new File(so.getFile().getParentFile(), "analysis");
                    dir = new File(dir, "tsne");
                    dir = new File(dir, "2_components");
                    if (!dir.exists())
                    {
                        throw new PipelineJobException("Unable to find barcode or analysis directory: " + dir.getPath());
                    }

                    perCellTsv = new File(dir, "projection.csv");
                }
                else
                {
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

                    perCellTsv = new File(children[0], "barcodes.tsv");
                }

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
            ctx.getLogger().debug("inspecting file: " + perCellTsv.getPath());

            CellRangerVDJUtils utils = new CellRangerVDJUtils(ctx.getLogger(), ctx.getSourceDirectory());

            Map<Integer, Integer> readsetToHashing = CellRangerVDJUtils.getCachedReadsetMap(ctx.getSequenceSupport());
            ctx.getLogger().debug("total cashed readset/HTO pairs: " + readsetToHashing.size());

            //prepare whitelist of cell indexes
            File cellBarcodeWhitelist = utils.getValidCellIndexFile();
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
            File htoBarcodeWhitelist = utils.getValidHashingBarcodeFile();
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
            File cellToHto = utils.getCellToHtoFile();
            File citeSeqCountUnknownOutput = new File(cellToHto.getParentFile(), "citeSeqUnknownBarcodes.txt");

            List<String> extraParams = new ArrayList<>();
            extraParams.add("-u");
            extraParams.add(citeSeqCountUnknownOutput.getPath());

            extraParams.addAll(getClientCommandArgs(ctx.getParams()));

            cellToHto = SequencePipelineService.get().runCiteSeqCount(htoReadset, htoBarcodeWhitelist, cellBarcodeWhitelist, cellToHto.getParentFile(), FileUtil.getBaseName(cellToHto.getName()), ctx.getLogger(), extraParams);
            ctx.getFileManager().addOutput(action, "CiteSeqCount Counts", cellToHto);
            ctx.getFileManager().addOutput(action,"CiteSeqCount Unknown Barcodes", citeSeqCountUnknownOutput);

            ctx.getFileManager().addSequenceOutput(cellToHto, rs.getName() + ": Cell Hashing Calls", "10x GEX Cell Hashing Calls", rs.getReadsetId(), null, genomeId, null);

            File forLoupe = new File(ctx.getSourceDirectory(), rs.getName() + "-CiteSeqCalls.csv");
            try (CSVReader reader = new CSVReader(Readers.getReader(cellToHto), '\t');CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(forLoupe), ',', CSVWriter.NO_QUOTE_CHARACTER))
            {
                writer.writeNext(new String[]{"CellBarcode", "HTO"});
                String[] line;
                int idx = 0;
                while ((line = reader.readNext()) != null)
                {
                    idx++;

                    if (idx > 1)
                    {
                        line[0] = line[0] + "-1";
                    }

                    writer.writeNext(line);
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
            ctx.getFileManager().addSequenceOutput(forLoupe, rs.getName() + ": Cell Hashing Calls", "10x GEX Cell Hashing Calls (Loupe)", rs.getReadsetId(), null, genomeId, null);

            if (citeSeqCountUnknownOutput.exists())
            {
                Map<String, String> allBarcodes = CellRangerVDJUtils.readAllBarcodes(ctx.getSourceDirectory());
                CellRangerVDJUtils.logTopUnknownBarcodes(citeSeqCountUnknownOutput, ctx.getLogger(), allBarcodes);
            }

            return cellToHto;
        }
    }
}