package org.labkey.mgap.pipeline;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.IOUtil;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Results;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractGatkWrapper;
import org.labkey.api.sequenceanalysis.run.GeneToNameTranslator;
import org.labkey.api.sequenceanalysis.run.SelectVariantsWrapper;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.mgap.mGAPManager;
import org.labkey.mgap.mGAPModule;
import org.labkey.mgap.mGAPSchema;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by bimber on 5/2/2017.
 */
public class PublicReleaseHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private final FileType _vcfType = new FileType(Arrays.asList(".vcf"), ".vcf", false, FileType.gzSupportLevel.SUPPORT_GZ);

    public PublicReleaseHandler()
    {
        super(ModuleLoader.getInstance().getModule(mGAPModule.class), "Create mGAP Release", "This will prepare an input VCF for use as an mGAP public release.  This will optionally include: removing excess annotations and program records, limiting to SNVs (optional) and removing genotype data (optional).  If genotypes are retained, the subject names will be checked for mGAP aliases and replaced as needed.", null, Arrays.asList(
                ToolParameterDescriptor.create("releaseVersion", "Version", "This string will be used as the version when published.", "textfield", new JSONObject(){{
                    put("allowBlank", false);
                }}, null),
                ToolParameterDescriptor.create("removeAnnotations", "Remove Most Annotations", "If selected, most annotations and extraneous information will be removed.  This is both to trim down the size of the public VCF and to shield some information.", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, null),
                ToolParameterDescriptor.create("snvOnly", "Limit To SNVs", "If selected, only variants of the type SNV will be included.", "checkbox", new JSONObject()
                {{
                    put("checked", false);
                }}, false),
                ToolParameterDescriptor.createExpDataParam("gtfFile", "GTF File", "The gene file used to create these annotations.", "sequenceanalysis-genomefileselectorfield", new JSONObject()
                {{
                    put("extensions", Arrays.asList("gtf"));
                    put("width", 400);
                    put("allowBlank", false);
                }}, null),
                ToolParameterDescriptor.create("variantTableOnly", "Variant Table Only", "If selected, the input VCF will be used as-is, and the only output will be the table of significant variants.  This was created mostly for testing purposes.", "checkbox", new JSONObject()
                {{
                    put("checked", false);
                }}, false),
                ToolParameterDescriptor.create("testOnly", "Test Only", "If selected, the various files will be created, but a record will not be created in the relases table, meaning it will not be synced to mGAP.", "checkbox", new JSONObject()
                {{
                    put("checked", false);
                }}, false)
        ));
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && o.getFile().exists() && _vcfType.isType(o.getFile());
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
        return new Processor();
    }

    public static class Processor implements SequenceOutputProcessor
    {
        public Processor()
        {

        }

        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            job.getLogger().info("writing track/subset data to file");
            TableInfo releaseTrackSubsets = QueryService.get().getUserSchema(job.getUser(), (job.getContainer().isWorkbook() ? job.getContainer().getParent() : job.getContainer()), mGAPSchema.NAME).getTable(mGAPSchema.TABLE_RELEASE_TRACK_SUBSETS);

            Set<FieldKey> toSelect = new HashSet<>();
            toSelect.add(FieldKey.fromString("trackName"));
            toSelect.add(FieldKey.fromString("subjectId"));
            toSelect.add(FieldKey.fromString("trackName/isprimarytrack"));
            toSelect.add(FieldKey.fromString("trackName/vcfId"));
            Map<FieldKey, ColumnInfo> colMap = QueryService.get().getColumns(releaseTrackSubsets, toSelect);

            Set<String> distinctTracks = new HashSet<>();
            Set<String> distinctSubjects = new HashSet<>();
            File trackFile = getTrackFile(outputDir);
            try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(trackFile), '\t', CSVWriter.NO_QUOTE_CHARACTER))
            {
                new TableSelector(releaseTrackSubsets, colMap.values(), null, null).forEachResults(rs -> {
                    if (rs.getObject(FieldKey.fromString("trackName/vcfId")) != null)
                    {
                        job.getLogger().info("skipping row b/c it has a VCF already: " + FieldKey.fromString("trackName"));
                        return;
                    }

                    writer.writeNext(new String[]{
                            rs.getString(FieldKey.fromString("trackName")),
                            rs.getString(FieldKey.fromString("subjectId")),
                            String.valueOf(rs.getBoolean(FieldKey.fromString("trackName/isprimarytrack")))
                    });

                    distinctTracks.add(rs.getString(FieldKey.fromString("trackName")));
                    distinctSubjects.add(rs.getString(FieldKey.fromString("subjectId")));
                });
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            job.getLogger().info("total tracks: " + distinctTracks.size());

            File outputFile = getSampleNameFile(((FileAnalysisJobSupport)job).getAnalysisDirectory());
            job.getLogger().debug("caching mGAP aliases to file: " + outputFile.getPath());

            Map<String, String> sampleNameMap = new HashMap<>();
            for (SequenceOutputFile so : inputFiles)
            {
                try
                {
                    SequenceAnalysisService.get().ensureVcfIndex(so.getFile(), job.getLogger());
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                try (VCFFileReader reader = new VCFFileReader(so.getFile()))
                {
                    VCFHeader header = reader.getFileHeader();
                    TableInfo ti = QueryService.get().getUserSchema(job.getUser(), (job.getContainer().isWorkbook() ? job.getContainer().getParent() : job.getContainer()), mGAPSchema.NAME).getTable(mGAPSchema.TABLE_ANIMAL_MAPPING);
                    TableSelector ts = new TableSelector(ti, PageFlowUtil.set("subjectname", "externalAlias"), new SimpleFilter(FieldKey.fromString("subjectname"), distinctSubjects, CompareType.IN), null);
                    ts.forEachResults(new Selector.ForEachBlock<Results>()
                    {
                        @Override
                        public void exec(Results rs) throws SQLException
                        {
                            sampleNameMap.put(rs.getString(FieldKey.fromString("subjectname")), rs.getString(FieldKey.fromString("externalAlias")));
                        }
                    });

                    Set<String> sampleNames = new HashSet<>(header.getSampleNamesInOrder());
                    job.getLogger().info("total samples in input VCF: " + sampleNames.size());

                    sampleNames.retainAll(distinctSubjects);
                    job.getLogger().info("total samples to be written to any track: " + sampleNames.size());
                    if (sampleNames.size() != distinctSubjects.size())
                    {
                        Set<String> samplesMissing = new HashSet<>(distinctSubjects);
                        samplesMissing.removeAll(header.getSampleNamesInOrder());
                        job.getLogger().warn("not all samples requested in tracks found in VCF.  missing: " + StringUtils.join(samplesMissing, ","));
                    }

                    sampleNames.removeAll(sampleNameMap.keySet());
                    if (!sampleNames.isEmpty())
                    {
                        throw new PipelineJobException("mGAP Aliases were not found for all IDs.  Missing: " + StringUtils.join(sampleNames, ", "));
                    }

                    //also list samples in VCF that will not be used:
                    sampleNames = new HashSet<>(header.getSampleNamesInOrder());
                    sampleNames.removeAll(distinctSubjects);
                    if (!sampleNames.isEmpty())
                    {
                        job.getLogger().info("the following samples are in the VCF but not selected to use in any track: " + StringUtils.join(sampleNames, ","));
                    }
                }
            }

            job.getLogger().info("total sample names to alias: " + sampleNameMap.size());
            try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(outputFile), '\t', CSVWriter.NO_QUOTE_CHARACTER))
            {
                for (String name : sampleNameMap.keySet())
                {
                    writer.writeNext(new String[]{name, sampleNameMap.get(name)});
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        private File getTrackFile(File outputDir)
        {
            return new File(outputDir, "releaseTracks.txt");
        }

        private File getSampleNameFile(File outputDir)
        {
            return new File(outputDir, "sampleMapping.txt");
        }

        @Override
        public void complete(PipelineJob job, List<SequenceOutputFile> inputs, List<SequenceOutputFile> outputsCreated) throws PipelineJobException
        {
            if (outputsCreated.isEmpty())
            {
                job.getLogger().error("no outputs found");
            }

            Map<String, SequenceOutputFile> outputVCFMap = new HashMap<>();
            Map<String, SequenceOutputFile> outputTableMap = new HashMap<>();
            Map<String, SequenceOutputFile> trackVCFMap = new HashMap<>();

            for (SequenceOutputFile so : outputsCreated)
            {
                if (so.getRowid() == null || so.getRowid() == 0)
                {
                    throw new PipelineJobException("No rowId found for sequence output");
                }

                if (so.getCategory().endsWith("Table"))
                {
                    String name = so.getName().replaceAll(" Variant Table", "");
                    outputTableMap.put(name, so);
                }
                else if (so.getCategory().endsWith("Release"))
                {
                    outputVCFMap.put(so.getName(), so);
                }
                else if (so.getCategory().endsWith("Release Track"))
                {
                    trackVCFMap.put(so.getName(), so);
                }
                else
                {
                    throw new PipelineJobException("Unexpected output: " + so.getCategory());
                }
            }

            //save all DB inserts for list:
            List<Map<String, Object>> variantReleaseRows = new ArrayList<>();
            List<Map<String, Object>> variantTableRows = new ArrayList<>();
            List<Map<String, Object>> releaseStatsRows = new ArrayList<>();
            List<Map<String, Object>> tracksPerReleaseRows = new ArrayList<>();

            boolean testOnly = StringUtils.isEmpty(job.getParameters().get("testOnly")) ? false : ConvertHelper.convert(job.getParameters().get("testOnly"), boolean.class);

            for (String release : outputVCFMap.keySet())
            {
                SequenceOutputFile so = outputVCFMap.get(release);
                SequenceOutputFile so2 = outputTableMap.get(release);
                if (so2 == null)
                {
                    throw new PipelineJobException("Unable to find table output for release: " + release);
                }

                //find basic stats:
                job.getLogger().info("inspecting file: " + so.getName());
                int totalSubjects;
                long totalVariants = 0;
                try (VCFFileReader reader = new VCFFileReader(so.getFile()))
                {
                    totalSubjects = reader.getFileHeader().getSampleNamesInOrder().size();
                    try (CloseableIterator<VariantContext> it = reader.iterator())
                    {
                        while (it.hasNext())
                        {
                            VariantContext vc = it.next();
                            if (vc.isFiltered())
                            {
                                throw new PipelineJobException("The published VCF should not contain filtered sites");
                            }

                            totalVariants++;
                            if (totalVariants % 1000000 == 0)
                            {
                                job.getLogger().info("processed " + totalVariants + " sites");
                            }
                        }
                    }
                }

                //actually create release record
                Map<String, Object> row = new CaseInsensitiveHashMap<>();
                row.put("version", job.getParameters().get("releaseVersion"));
                row.put("releaseDate", new Date());
                row.put("vcfId", so.getRowid());
                row.put("variantTable", so2.getRowid());
                row.put("genomeId", so.getLibrary_id());
                row.put("totalSubjects", totalSubjects);
                row.put("totalVariants", totalVariants);
                String guid = new GUID().toString();
                row.put("objectId", guid);

                try
                {
                    if (!testOnly){
                        variantReleaseRows.add(row);

                        File variantTable = so2.getFile();
                        if (variantTable.exists())
                        {
                            try (CSVReader reader = new CSVReader(IOUtil.openFileForBufferedReading(variantTable), '\t'))
                            {
                                String[] line;
                                int lineNo = 0;
                                while ((line = reader.readNext()) != null)
                                {
                                    lineNo++;
                                    if (lineNo == 1)
                                    {
                                        continue; //header
                                    }

                                    Map<String, Object> map = new CaseInsensitiveHashMap<>();
                                    map.put("releaseId", guid);
                                    map.put("contig", line[0]);
                                    map.put("position", line[1]);
                                    map.put("reference", line[2]);
                                    map.put("allele", line[3]);
                                    map.put("source", line[4]);
                                    map.put("reason", line[5]);
                                    map.put("description", line[6]);
                                    map.put("overlappingGenes", line[7]);
                                    map.put("omim", updateOmimD(line[8], job.getContainer(), job.getLogger()));
                                    map.put("omim_phenotype", updateOmimD(line[9], job.getContainer(), job.getLogger()));
                                    map.put("af", line[10]);
                                    map.put("objectId", new GUID().toString());

                                    variantTableRows.add(map);
                                }
                            }
                        }
                        else
                        {
                            job.getLogger().error("unable to find release stats file: " + variantTable.getPath());
                        }

                        File releaseStats = new File(so.getFile().getParentFile(), SequenceAnalysisService.get().getUnzippedBaseName(so.getFile().getName()) + ".summaryByField.txt");
                        if (releaseStats.exists())
                        {
                            try (CSVReader reader = new CSVReader(IOUtil.openFileForBufferedReading(releaseStats), '\t'))
                            {
                                String[] line;
                                int lineNo = 0;
                                while ((line = reader.readNext()) != null)
                                {
                                    lineNo++;
                                    if (lineNo == 1)
                                    {
                                        continue; //header
                                    }

                                    Map<String, Object> map = new CaseInsensitiveHashMap<>();
                                    map.put("releaseId", guid);
                                    map.put("category", line[0]);
                                    map.put("metricName", line[1]);
                                    map.put("value", line[2]);
                                    map.put("objectId", new GUID().toString());
                                    releaseStatsRows.add(map);
                                }
                            }
                        }
                        else
                        {
                            job.getLogger().error("unable to find release stats file: " + releaseStats.getPath());
                        }

                        //also tracks:
                        UserSchema us = QueryService.get().getUserSchema(job.getUser(), job.getContainer().isWorkbook() ? job.getContainer().getParent() : job.getContainer(), mGAPSchema.NAME);
                        new TableSelector(us.getTable(mGAPSchema.TABLE_RELEASE_TRACKS), null, null).forEachResults(rs -> {
                            SequenceOutputFile so3 = trackVCFMap.get(rs.getString(FieldKey.fromString("trackName")));
                            if (so3 == null && rs.getBoolean(FieldKey.fromString("isprimarytrack")))
                            {
                                //this is the primary track
                                so3 = so;
                            }

                            if (so3 == null && rs.getObject("vcfId") == null)
                            {
                                job.getLogger().error("unable to find track with name: " + rs.getString(FieldKey.fromString("trackName")));
                                return;
                            }

                            Map<String, Object> map = new CaseInsensitiveHashMap<>();
                            map.put("trackName", rs.getString(FieldKey.fromString("trackName")));
                            map.put("label", rs.getString(FieldKey.fromString("label")));
                            map.put("category", rs.getString(FieldKey.fromString("category")));
                            map.put("description", rs.getString(FieldKey.fromString("description")));
                            map.put("isprimarytrack", rs.getBoolean(FieldKey.fromString("isprimarytrack")));
                            map.put("url", rs.getString(FieldKey.fromString("url")));
                            map.put("releaseId", guid);
                            map.put("vcfId", so3 == null ? rs.getInt(FieldKey.fromString("vcfId")) : so3.getRowid());
                            map.put("objectId", new GUID().toString());

                            tracksPerReleaseRows.add(map);
                        });
                    }
                    else
                    {
                        job.getLogger().info("This was selected as a test-only run, so skipping creation of release record");
                        job.getLogger().info("variant release record values:");
                        for (String field : row.keySet())
                        {
                            job.getLogger().info(field + ": " + row.get(field));
                        }
                    }
                }
                catch (Exception e)
                {
                    throw new PipelineJobException("Error parsing data: " + e.getMessage(), e);
                }
            }

            //finally do actual DB inserts, if successful
            if (!testOnly)
            {
                try (DbScope.Transaction transaction = DbScope.getLabKeyScope().ensureTransaction())
                {
                    job.getLogger().info("Publishing release to variant catalog table");
                    TableInfo variantReleaseTable = QueryService.get().getUserSchema(job.getUser(), job.getContainer(), mGAPSchema.NAME).getTable(mGAPSchema.TABLE_VARIANT_CATALOG_RELEASES);
                    BatchValidationException errors = new BatchValidationException();
                    variantReleaseTable.getUpdateService().insertRows(job.getUser(), job.getContainer(), variantReleaseRows, errors, null, new HashMap<>());
                    if (errors.hasErrors())
                    {
                        throw errors;
                    }

                    TableInfo variantTableInfo = QueryService.get().getUserSchema(job.getUser(), job.getContainer(), mGAPSchema.NAME).getTable(mGAPSchema.TABLE_VARIANT_TABLE);
                    BatchValidationException errors2 = new BatchValidationException();
                    variantTableInfo.getUpdateService().insertRows(job.getUser(), job.getContainer(), variantTableRows, errors2, null, new HashMap<>());
                    if (errors2.hasErrors())
                    {
                        throw errors2;
                    }

                    job.getLogger().info("total variant table records: " + variantTableRows.size());

                    TableInfo releaseStatsTable = QueryService.get().getUserSchema(job.getUser(), job.getContainer(), mGAPSchema.NAME).getTable(mGAPSchema.TABLE_RELEASE_STATS);
                    BatchValidationException errors3 = new BatchValidationException();
                    releaseStatsTable.getUpdateService().insertRows(job.getUser(), job.getContainer(), releaseStatsRows, errors3, null, new HashMap<>());
                    if (errors3.hasErrors())
                    {
                        throw errors3;
                    }

                    job.getLogger().info("total release stat records: " + releaseStatsRows.size());

                    TableInfo tracksPerReleaseTable = QueryService.get().getUserSchema(job.getUser(), job.getContainer(), mGAPSchema.NAME).getTable(mGAPSchema.TABLE_TRACKS_PER_RELEASE);
                    BatchValidationException errors4 = new BatchValidationException();
                    tracksPerReleaseTable.getUpdateService().insertRows(job.getUser(), job.getContainer(), tracksPerReleaseRows, errors4, null, new HashMap<>());
                    if (errors4.hasErrors())
                    {
                        throw errors4;
                    }

                    job.getLogger().info("total tracks per release records: " + tracksPerReleaseRows.size());

                    transaction.commit();
                }
                catch (Exception e)
                {
                    throw new PipelineJobException("Error saving data: " + e.getMessage(), e);
                }
            }
        }

        Map<String, String> omimMap = new HashMap<>();

        public String updateOmimD(String input, Container c, Logger log)
        {
            if (input.contains("<>"))
            {
                String[] parts = input.split("<>");
                input = parts.length == 1 ? parts[0] : parts[1];
            }

            if (omimMap.containsKey(input))
            {
                return omimMap.get(input);
            }

            String ret = input;
            String apiKey = mGAPManager.get().getOmimApiKey(c);
            if (apiKey == null)
            {
                log.error("OMIM APIKey not set");
                omimMap.put(input, input);
                return input;
            }

            try
            {
                String resolved = getOmimJson(input, apiKey, log);
                if (resolved != null && !resolved.equals(input))
                {
                    //OMIM gene entries default to containing semicolons, which is our delimiter in this field
                    resolved = resolved.replaceAll(";", ",");
                    ret = resolved + "<>" + input;
                }
            }
            catch (IOException e)
            {
                log.error(e);
            }

            omimMap.put(input, ret);

            return ret;
        }

        private String getOmimJson(String input, String apiKey, Logger log) throws IOException
        {
            String url = "https://api.omim.org/api/entry?mimNumber=" + input + "&apiKey=" + apiKey + "&format=json";
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            if (con.getResponseCode() != HttpURLConnection.HTTP_OK)
            {
                log.error("bad request: " + url);
                return null;
            }

            try (BufferedReader in = Readers.getReader(con.getInputStream()))
            {
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null)
                {
                    response.append(inputLine);
                }

                JSONObject json = new JSONObject(response.toString());
                if (json.containsKey("omim"))
                {
                    json = json.getJSONObject("omim");
                    if (json.containsKey("entryList"))
                    {
                        for (JSONObject j : json.getJSONArray("entryList").toJSONObjectArray())
                        {
                            String val = j.getJSONObject("entry").getJSONObject("titles").optString("preferredTitle", input);
                            if (val.contains(";"))
                            {
                                String[] tokens = val.split(";");
                                String id = StringUtils.trimToNull(tokens[tokens.length - 1]);
                                String name = StringUtils.join(Arrays.asList(Arrays.copyOf(tokens, tokens.length-1)), ";");
                                val = name + " (" + id + ")";
                            }

                            return val;
                        }
                    }
                }
            }

            return null;
        }


        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            //first build map of gene to geneName:
            ctx.getLogger().info("building map of gene symbol to name:");
            int gtfId = ctx.getParams().getInt("gtfFile");
            File gtf = ctx.getSequenceSupport().getCachedData(gtfId);
            if (gtf == null || !gtf.exists())
            {
                throw new PipelineJobException("Unable to find file: " + gtf);
            }

            GeneToNameTranslator translator = new GeneToNameTranslator(gtf, ctx.getLogger());

            for (SequenceOutputFile so : inputFiles)
            {
                ReferenceGenome genome = ctx.getSequenceSupport().getCachedGenome(so.getLibrary_id());

                RecordedAction action = new RecordedAction();
                action.addInput(so.getFile(), "Input VCF");
                action.addInput(new File(so.getFile().getPath() + ".tbi"), "Input VCF Index");

                boolean variantTableOnly = ctx.getParams().optBoolean("variantTableOnly", false);
                //if variantTableOnly is selected, automatically ignore all these.
                boolean removeAnnotations = !variantTableOnly && ctx.getParams().optBoolean("removeAnnotations", false);
                boolean snvOnly = !variantTableOnly && ctx.getParams().optBoolean("snvOnly", false);
                String releaseVersion = ctx.getParams().optString("releaseVersion", "0.0");

                File currentVCF = so.getFile();

                //count subjects
                int totalSubjects = 0;
                try (VCFFileReader reader = new VCFFileReader(so.getFile()))
                {
                    totalSubjects = reader.getFileHeader().getSampleNamesInOrder().size();
                }

                //remove removeAnnotations
                if (removeAnnotations)
                {
                    File outputFile = new File(ctx.getOutputDir(), SequenceAnalysisService.get().getUnzippedBaseName(currentVCF.getName()) + ".noAnnotations.vcf.gz");
                    if (indexExists(outputFile))
                    {
                        ctx.getLogger().info("re-using existing output: " + outputFile.getPath());
                    }
                    else
                    {
                        new AbstractGatkWrapper(ctx.getLogger())
                        {
                            public void execute(File input, File outputFile, File referenceFasta) throws PipelineJobException
                            {
                                List<String> args = new ArrayList<>();
                                args.add(SequencePipelineService.get().getJavaFilepath());
                                args.addAll(SequencePipelineService.get().getJavaOpts());
                                args.add("-jar");
                                File gatkJar = getJAR();
                                gatkJar = new File(getJAR().getParentFile(), FileUtil.getBaseName(gatkJar) + "-discvr.jar");
                                args.add(gatkJar.getPath());
                                args.add("-T");
                                args.add("RemoveAnnotations");
                                args.add("-R");
                                args.add(referenceFasta.getPath());
                                args.add("-V");
                                args.add(input.getPath());
                                args.add("-o");
                                args.add(outputFile.getPath());

                                for (String key : Arrays.asList("AF", "AC", "END", "ANN", "LOF", "MAF", "CADD_PH", "CADD_RS", "CCDS", "ENC", "ENCDNA_CT", "ENCDNA_SC", "ENCSEG_CT", "ENCSEG_NM", "ENCTFBS_CL", "ENCTFBS_SC", "ENCTFBS_TF", "ENN", "ERBCTA_CT", "ERBCTA_NM", "ERBCTA_SC", "ERBSEG_CT", "ERBSEG_NM", "ERBSEG_SC", "ERBSUM_NM", "ERBSUM_SC", "ERBTFBS_PB", "ERBTFBS_TF", "FC", "FE", "FS_EN", "FS_NS", "FS_SC", "FS_SN", "FS_TG", "FS_US", "FS_WS", "GRASP_AN", "GRASP_P", "GRASP_PH", "GRASP_PL", "GRASP_PMID", "GRASP_RS", "LOF", "NC", "NE", "NF", "NG", "NH", "NJ", "NK", "NL", "NM", "NMD", "OMIMC", "OMIMD", "OMIMM", "OMIMMUS", "OMIMN", "OMIMS", "OMIMT", "OREGANNO_PMID", "OREGANNO_TYPE", "PC_PL", "PC_PR", "PC_VB", "PP_PL", "PP_PR", "PP_VB", "RDB_MF", "RDB_WS", "RFG", "RSID", "SCSNV_ADA", "SCSNV_RS", "SD", "SF", "SM", "SP_SC", "SX", "TMAF", "LF", "CLN_ALLELE", "CLN_ALLELEID", "CLN_DN", "CLN_DNINCL", "CLN_DISDB", "CLN_DISDBINCL", "CLN_HGVS", "CLN_REVSTAT", "CLN_SIG", "CLN_SIGINCL", "CLN_VC", "CLN_VCSO", "CLN_VI", "CLN_DBVARID", "CLN_GENEINFO", "CLN_MC", "CLN_ORIGIN", "CLN_RS", "CLN_SSR", "ReverseComplementedAlleles"))
                                {
                                    args.add("-A");
                                    args.add(key);
                                }

                                //for (String key : Arrays.asList("DP", "AD"))
                                //{
                                //    args.add("-GA");
                                //    args.add(key);
                                //}

                                args.add("-ef");
                                args.add("--clearGenotypeFilter");

                                super.execute(args);
                            }
                        }.execute(currentVCF, outputFile, genome.getWorkingFastaFile());
                    }

                    currentVCF = outputFile;
                    ctx.getFileManager().addIntermediateFile(outputFile);
                    ctx.getFileManager().addIntermediateFile(new File(outputFile.getPath() + ".tbi"));
                }

                //SNPs only:
                if (snvOnly)
                {
                    File outputFile = new File(ctx.getOutputDir(), SequenceAnalysisService.get().getUnzippedBaseName(currentVCF.getName()) + ".snv.vcf.gz");
                    if (indexExists(outputFile))
                    {
                        ctx.getLogger().info("re-using existing output: " + outputFile.getPath());
                    }
                    else
                    {
                        SelectVariantsWrapper wrapper = new SelectVariantsWrapper(ctx.getLogger());
                        wrapper.execute(genome.getWorkingFastaFile(), currentVCF, outputFile, Arrays.asList("--selectTypeToInclude", "SNP"));
                    }
                    currentVCF = outputFile;
                    ctx.getFileManager().addIntermediateFile(outputFile);
                    ctx.getFileManager().addIntermediateFile(new File(outputFile.getPath() + ".tbi"));
                }

                if (!variantTableOnly)
                {
                    currentVCF = renameSamples(currentVCF, genome, ctx);
                    ctx.getFileManager().addIntermediateFile(currentVCF);
                    ctx.getFileManager().addIntermediateFile(new File(currentVCF.getPath() + ".tbi"));
                }

                ctx.getLogger().info("splitting VCF by track:");
                String primaryTrackName = getPrimaryTrackName(getTrackFile(ctx.getSourceDirectory()));
                Map<String, List<String>> subjectByTrack = parseTrackMap(getTrackFile(ctx.getSourceDirectory()));

                File primaryTrackVcf = null;
                for (String trackName : subjectByTrack.keySet())
                {
                    ctx.getLogger().info("track: " + trackName + ", total samples: " + subjectByTrack.get(trackName).size());
                    File outputFile = new File(FileUtil.makeLegalName(trackName).replaceAll(" ", "_") + ".vcf.gz");
                    boolean isPrimaryTrack = trackName.equals(primaryTrackName);
                    if (isPrimaryTrack)
                    {
                        ctx.getLogger().info("this is the primary track");
                        outputFile = new File(ctx.getOutputDir(), "mGap.v" + FileUtil.makeLegalName(releaseVersion).replaceAll(" ", "_") + ".vcf.gz");
                        primaryTrackVcf = outputFile;
                    }

                    if (indexExists(outputFile))
                    {
                        ctx.getLogger().info("re-using existing output: " + outputFile.getPath());
                    }
                    else
                    {
                        if (outputFile.exists())
                        {
                            ctx.getLogger().info("deleting existing file: " + outputFile.getPath());
                            outputFile.delete();
                        }

                        Map<String, String> sampleMap = parseSampleMap(getSampleNameFile(ctx.getSourceDirectory()));
                        SelectVariantsWrapper wrapper = new SelectVariantsWrapper(ctx.getLogger());
                        List<String> args = new ArrayList<>();
                        args.add("-env");
                        args.add("-noTrim"); //in order to keep annotations correct
                        subjectByTrack.get(trackName).forEach(x -> {
                            args.add("-sn");
                            args.add(sampleMap.get(x));
                        });
                        wrapper.execute(genome.getWorkingFastaFile(), currentVCF, outputFile, args);

                        ctx.getLogger().info("total sites: " + SequenceAnalysisService.get().getVCFLineCount(outputFile, ctx.getLogger(), false));
                    }

                    ctx.getFileManager().removeIntermediateFile(outputFile);
                    ctx.getFileManager().removeIntermediateFile(new File(outputFile.getPath() + ".tbi"));

                    //make outputs, summarize:
                    boolean testOnly = ctx.getParams().optBoolean("testOnly", false);
                    if (isPrimaryTrack)
                    {
                        ctx.getLogger().info("inspecting primary VCF and creating summary table");
                        inspectAndSummarizeVcf(ctx, primaryTrackVcf, translator, genome, true);
                        if (!variantTableOnly)
                        {
                            SequenceOutputFile output = new SequenceOutputFile();
                            output.setFile(primaryTrackVcf);
                            output.setName("mGAP Release: " + releaseVersion);
                            output.setCategory((testOnly ? "Test " : "") + "mGAP Release");
                            output.setLibrary_id(genome.getGenomeId());
                            ctx.getFileManager().addSequenceOutput(output);

                            File interestingVariantTable = getVariantTableName(ctx, primaryTrackVcf);
                            SequenceOutputFile output2 = new SequenceOutputFile();
                            output2.setFile(interestingVariantTable);
                            output2.setName("mGAP Release: " + releaseVersion + " Variant Table");
                            output2.setCategory((testOnly ? "Test " : "") + "mGAP Release Variant Table");
                            output2.setLibrary_id(genome.getGenomeId());
                            ctx.getFileManager().addSequenceOutput(output2);
                        }
                    }
                    else
                    {
                        if (!variantTableOnly)
                        {
                            SequenceOutputFile output = new SequenceOutputFile();
                            output.setFile(outputFile);
                            output.setName(trackName);
                            output.setCategory((testOnly ? "Test " : "") + "mGAP Release Track");
                            output.setLibrary_id(genome.getGenomeId());
                            ctx.getFileManager().addSequenceOutput(output);
                        }
                    }
                }

                if (primaryTrackVcf == null)
                {
                    throw new PipelineJobException("No VCF marked as the primary track");
                }
            }
        }

        private File getVariantTableName(JobContext ctx, File vcfInput)
        {
            return new File(ctx.getOutputDir(), SequenceAnalysisService.get().getUnzippedBaseName(vcfInput.getName()) + ".variants.txt");
        }

        private void inspectAndSummarizeVcf(JobContext ctx, File vcfInput, GeneToNameTranslator translator, ReferenceGenome genome, boolean generateSummaries) throws PipelineJobException
        {
            long sitesInspected = 0L;
            long totalVariants = 0L;
            long totalPrivateVariants = 0L;
            Map<VariantContext.Type, Long> typeCounts = new HashMap<>();

            File interestingVariantTable = getVariantTableName(ctx, vcfInput);
            try (VCFFileReader reader = new VCFFileReader(vcfInput); CloseableIterator<VariantContext> it = reader.iterator(); CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(interestingVariantTable), '\t', CSVWriter.NO_QUOTE_CHARACTER))
            {
                writer.writeNext(new String[]{"Chromosome", "Position", "Reference", "Allele", "Source", "Reason", "Description", "Overlapping Gene(s)", "OMIM Entries", "OMIM Phenotypes", "AF"});
                while (it.hasNext())
                {
                    Set<List<String>> queuedLines = new LinkedHashSet<>();

                    sitesInspected++;

                    if (sitesInspected % 1000000 == 0)
                    {
                        ctx.getLogger().info("inspected " + sitesInspected + " variants");
                    }

                    VariantContext vc = it.next();
                    if (vc.isFiltered())
                    {
                        continue;
                    }

                    totalVariants++;

                    //track total by variant type
                    Long typeCount = typeCounts.get(vc.getType());
                    if (typeCount == null)
                    {
                        typeCount = 0L;
                    }
                    typeCount++;
                    typeCounts.put(vc.getType(), typeCount);

                    //count private alleles.  note: this is counting alleles, not sites
                    for (Allele a : vc.getAlleles())
                    {
                        int sampleCount = 0;
                        for (Genotype g : vc.getGenotypes())
                        {
                            if (g.getAlleles().contains(a))
                            {
                                sampleCount++;
                                if (sampleCount > 1)
                                {
                                    break;
                                }
                            }
                        }

                        if (sampleCount == 1)
                        {
                            totalPrivateVariants++;
                        }
                    }

                    Set<String> omims = new LinkedHashSet<>();
                    Set<String> omimds = new LinkedHashSet<>();
                    if (vc.getAttribute("OMIMN") != null)
                    {
                        omims.add(vc.getAttributeAsString("OMIMN", null));
                    }

                    if (vc.getAttribute("OMIMD") != null)
                    {
                        if (vc.getAttribute("OMIMD") instanceof Collection || vc.getAttribute("OMIMD").getClass().isArray())
                        {
                            ctx.getLogger().warn("OMIMD non-string: " + vc.getAttribute("OMIMD").getClass().getName());
                            ctx.getLogger().warn(vc.getAttribute("OMIMD"));
                        }

                        Collection<String> vals = parseOmim(vc.getAttributeAsString("OMIMD", null), ctx.getLogger());
                        if (vals != null)
                        {
                            omimds.addAll(vals);
                        }
                    }

                    Set<String> overlappingGenes = new HashSet<>();
                    if (vc.getAttribute("ANN") != null)
                    {
                        List<String> anns = vc.getAttributeAsStringList("ANN", "");

                        //find overlapping genes first
                        for (String ann : anns)
                        {
                            if (StringUtils.isEmpty(ann))
                            {
                                continue;
                            }

                            String[] tokens = ann.split("\\|");
                            if (tokens.length < 4)
                            {
                                //intergenic modifiers
                                continue;
                            }

                            if (!StringUtils.isEmpty(tokens[3]))
                            {
                                String geneName = tokens[3];
                                if (geneName.startsWith("ENSMMUE"))
                                {
                                    //exons
                                    continue;
                                }

                                if (geneName.startsWith("gene:"))
                                {
                                    geneName = geneName.replaceAll("gene:", "");
                                }

                                if (translator.getGeneMap().containsKey(geneName) && translator.getGeneMap().get(geneName).get("gene_name") != null)
                                {
                                    geneName = translator.getGeneMap().get(geneName).get("gene_name");
                                }

                                overlappingGenes.add(geneName);
                            }
                        }

                        for (String ann : anns)
                        {
                            if (StringUtils.isEmpty(ann))
                            {
                                continue;
                            }

                            String[] tokens = ann.split("\\|");

                            if ("HIGH".equals(tokens[2]))
                            {
                                if (tokens.length < 10)
                                {
                                    ctx.getLogger().error("unexpected ANN line at pos: " + vc.getContig() + " " + vc.getStart() + "[" + tokens + "]");
                                    continue;
                                }

                                String description = "Type: " + (tokens[1].replaceAll("&", ", ")) + "; Gene: " + tokens[3];
                                if (tokens.length > 10 && !StringUtils.isEmpty(tokens[10]))
                                {
                                    description += "; AA Change: " + tokens[10];
                                }

                                maybeWriteVariantLine(queuedLines, vc, tokens[0], "SNPEff", "Predicted High Impact", description, overlappingGenes, omims, omimds, ctx.getLogger());
                            }
                        }
                    }

                    if (vc.getAttribute("CLN_SIG") != null)
                    {
                        List<String> clnAlleles = vc.getAttributeAsStringList("CLN_ALLELE", "");
                        List<String> clnSigs = vc.getAttributeAsStringList("CLN_SIG", "");
                        List<String> clnDisease = vc.getAttributeAsStringList("CLN_DN", "");
                        int i = -1;
                        for (String sigList : clnSigs)
                        {
                            i++;

                            List<String> sigSplit = Arrays.asList(sigList.split("\\|"));
                            List<String> diseaseSplit = Arrays.asList(clnDisease.get(i).split("\\|"));
                            int j = 0;
                            for (String sig : sigSplit)
                            {
                                //TODO: consider disease = not_provided
                                if (isAllowableClinVarSig(sig))
                                {
                                    String description = StringUtils.join(new String[]{
                                            "Significance: " + sig
                                    }, ",");

                                    String allele = clnAlleles.get(i);
                                    maybeWriteVariantLine(queuedLines, vc, allele, "ClinVar", diseaseSplit.get(j), description, overlappingGenes, omims, omimds, ctx.getLogger());
                                }

                                j++;
                            }
                        }
                    }

                    //NE: nsdb Polyphen2_HVAR_score: Polyphen2 score based on HumVar, i.e. hvar_prob. The score ranges from 0 to 1, and the corresponding prediction is 'probably damaging' if it is in [0.909,1], 'possibly damaging' if it is in [0.447,0.908], 'benign' if it is in [0,0.446]. Score cutoff for binary classification is 0.5, i.e. the prediction is 'neutral' if the score is smaller than 0.5 and 'deleterious' if the score is larger than 0.5. Multiple entries separated by
                    //NF: nsdb Polyphen2_HVAR_pred: Polyphen2 prediction based on HumVar, 'D' ('probably damaging'),'P' ('possibly damaging') and 'B' ('benign'). Multiple entries separated by
                    if (vc.getAttribute("NF") != null && !".".equals(vc.getAttribute("NF")))
                    {
                        Set<String> polyphenPredictions = new HashSet<>(vc.getAttributeAsStringList("NF", null));
                        polyphenPredictions.remove("B");
                        polyphenPredictions.remove("P");

                        if (!polyphenPredictions.isEmpty())
                        {
                            Double maxScore = Collections.max(vc.getAttributeAsDoubleList("NE", 0.0));
                            String description = StringUtils.join(new String[]{
                                    "Score: " + String.valueOf(maxScore)
                            }, ",");

                            maybeWriteVariantLine(queuedLines, vc, null, "Polyphen2", "Prediction: " + StringUtils.join(polyphenPredictions, ","), description, overlappingGenes, omims, omimds, ctx.getLogger());
                        }
                    }

                    for (List<String> line : queuedLines)
                    {
                        writer.writeNext(line.toArray(new String[line.size()]));
                    }
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            if (generateSummaries)
            {
                int totalSubjects;
                try (VCFFileReader reader = new VCFFileReader(vcfInput))
                {
                    totalSubjects = reader.getFileHeader().getSampleNamesInOrder().size();
                }

                generateSummaries(ctx, vcfInput, genome, totalVariants, totalPrivateVariants, totalSubjects, typeCounts);
            }
        }

        private boolean isAllowableClinVarSig(String x)
        {
            return !(StringUtils.isEmpty(x) || x.toLowerCase().contains("benign") || x.toLowerCase().contains("unknown") || x.toLowerCase().contains("uncertain") || x.contains("not_specified") || x.contains("not_provided"));
        }

        private void generateSummaries(JobContext ctx, File vcf, ReferenceGenome genome, long totalVariants, long totalPrivateVariants, int totalSubjects, Map<VariantContext.Type, Long> typeCounts) throws PipelineJobException
        {
            //variants to table
            ctx.getLogger().info("Running VariantsToTable");
            File variantsToTable = new File(ctx.getOutputDir(), SequenceAnalysisService.get().getUnzippedBaseName(vcf.getName()) + ".variantsToTable.txt");
            File tableCheck = new File(variantsToTable.getPath() + ".done");
            if (!tableCheck.exists())
            {
                VariantsToTableRunner vtt = new VariantsToTableRunner(ctx.getLogger());
                List<String> fields = new ArrayList<>(Arrays.asList("POS", "REF", "ALT", "FILTER"));
                fields.addAll(allowedFields);
                vtt.execute(vcf, variantsToTable, genome.getWorkingFastaFile(), fields);

                try
                {
                    FileUtils.touch(tableCheck);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }
            else
            {
                ctx.getLogger().info("resuming with existing file: " + variantsToTable.getPath());
            }

            ctx.getFileManager().addIntermediateFile(variantsToTable);
            ctx.getFileManager().addIntermediateFile(tableCheck);

            //generate stats
            ctx.getLogger().info("Generating summary stats from: " + variantsToTable.getName());
            File summaryTable = new File(vcf.getParentFile(), SequenceAnalysisService.get().getUnzippedBaseName(vcf.getName()) + ".summary.txt");
            File summaryTableByField = new File(vcf.getParentFile(), SequenceAnalysisService.get().getUnzippedBaseName(vcf.getName()) + ".summaryByField.txt");
            generateSummary(ctx, variantsToTable, summaryTable, summaryTableByField, totalVariants, totalPrivateVariants, totalSubjects, typeCounts);
        }

        private static final List<String> allowedFields = Arrays.asList("CHROM", "ANN", "CLN_SIG", "GRASP_PH", "ENCTFBS_TF", "FE", "NF", "ENCSEG_NM");

        private class FieldTracker
        {
            private Map<String, FieldData> perField;

            public FieldTracker(int size)
            {
                perField = new HashMap<>(size);
            }

            public void add(String fieldName, String val)
            {
                if (!allowedFields.contains(fieldName))
                {
                    return;
                }

                if (!StringUtils.isEmpty(val) && !"NA".equals(val))
                {
                    if ("ANN".equals(fieldName))
                    {
                        boolean isHighImpact = false;
                        Set<String> codingPotential = new HashSet<>();
                        String[] tokens = val.split(",");
                        for (String v : tokens)
                        {
                            String[] split = v.split("\\|");
                            if ("HIGH".equals(split[2]))
                            {
                                isHighImpact = true;
                            }

                            String[] types = split[1].split("&");
                            codingPotential.addAll(Arrays.asList(types));
                        }

                        if (isHighImpact)
                        {
                            addForValue("AnnotationSummary", "Predicted High Impact (SnpEff)");
                        }

                        //coding potential:
                        for (String type : codingPotential)
                        {
                            addForValue("CodingPotential", type);
                        }
                    }
                    else if ("CLN_SIG".equals(fieldName))
                    {
                        boolean hasOverlap = false;
                        boolean hasAnyOverlap = false;
                        String[] tokens = val.split(",");
                        for (String v : tokens)
                        {
                            if (StringUtils.isEmpty(v))
                            {
                                continue;
                            }

                            hasAnyOverlap = true;

                            String[] split = v.split("\\|");
                            for (String sig : split)
                            {
                                //TODO: consider disease = not_provided
                                if (isAllowableClinVarSig(sig))
                                {
                                    hasOverlap = true;
                                    break;
                                }
                            }
                        }

                        if (hasOverlap)
                        {
                            addForValue("AnnotationSummary", "ClinVar Overlap (Pathogenic)");
                        }

                        if (hasAnyOverlap)
                        {
                            addForValue("AnnotationSummary", "ClinVar Overlap");
                        }
                    }
                    else if ("GRASP_PH".equals(fieldName))
                    {
                        addForValue("AnnotationSummary", "GWAS Associations (GRASP)");
                    }
                    else if ("FE".equals(fieldName) && "Y".equals(val))
                    {
                        addForValue("AnnotationSummary", "Enhancer Region (FANTOM5)");
                    }
                    else if ("ENCTFBS_TF".equals(fieldName))
                    {
                        addForValue("AnnotationSummary", "Transcription Factor Binding (ENCODE)");
                    }
                    else if ("NF".equals(fieldName) && val.contains("D"))
                    {
                        addForValue("AnnotationSummary", "Damaging (Polyphen2)");
                    }
                    else if ("ENCSEG_NM".equals(fieldName))
                    {
                        List<String> values = Arrays.asList(val.split(","));
                        if (values.contains("E"))
                        {
                            addForValue("AnnotationSummary", "Predicted Enhancer (ENCODE)");
                        }
                    }
                    else if ("CHROM".equals(fieldName))
                    {
                        addForValue("PerChromosome", val);
                    }
                }
            }

            private void addForValue(String fieldName, String val)
            {
                if (allowForLevel(val))
                {
                    FieldData data = perField.get(fieldName);
                    if (data == null)
                    {
                        data = new FieldData();
                    }

                    Integer count = data.countsPerLevel.get(val);
                    if (count == null)
                    {
                        count = 0;
                    }
                    count++;

                    data.countsPerLevel.put(val, count);

                    perField.put(fieldName, data);
                }
            }

            private boolean allowForLevel(String val)
            {
                //if not numeric, accept it
                if (!NumberUtils.isCreatable(val))
                {
                    return true;
                }

                //otherwise allow only integers
                return NumberUtils.isDigits(val);
            }
        }

        private class FieldData
        {
            int nonNull = 0;
            Map<String, Integer> countsPerLevel = new HashMap<>(20);
        }

        private void generateSummary(JobContext ctx, File variantsToTable, File output, File outputPerValue, long totalVariants, long totalPrivateVariants, int totalSubjects, Map<VariantContext.Type, Long> typeCounts) throws PipelineJobException
        {
            ctx.getLogger().info("reading variant table");
            int lineNo = 0;
            FieldTracker tracker = new FieldTracker(130);
            try (BufferedReader reader = Readers.getReader(variantsToTable))
            {
                lineNo++;
                if (lineNo % 1000000 == 0)
                {
                    ctx.getLogger().info("processed " + lineNo + " lines");
                }

                String lineStr;
                List<String> header = new ArrayList<>();
                int lineCount = 0;
                while ((lineStr = reader.readLine()) != null)
                {
                    String[] line = lineStr.split("\t");
                    lineCount++;
                    if (lineCount == 1)
                    {
                        //skip header
                        header = Arrays.asList(line);
                        continue;
                    }

                    //skip basic site information, but do include CHROM, since that might be useful to see summarized
                    for (int i = 4; i < line.length; i++)
                    {
                        tracker.add(header.get(i), line[i]);
                    }
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            ctx.getLogger().info("writing summary tables");
            try (CSVWriter writer = new CSVWriter(IOUtil.openFileForBufferedWriting(output), '\t', CSVWriter.NO_QUOTE_CHARACTER); CSVWriter valWriter = new CSVWriter(IOUtil.openFileForBufferedWriting(outputPerValue), '\t', CSVWriter.NO_QUOTE_CHARACTER))
            {
                writer.writeNext(new String[]{"Field", "Category", "NonNull", "TotalDistinct", "Levels"});
                for (String fn : new TreeSet<>(tracker.perField.keySet()))
                {
                    FieldData data = tracker.perField.get(fn);

                    String vals = "";
                    if (data.countsPerLevel.size() < 10)
                    {
                        vals = StringUtils.join(data.countsPerLevel.keySet(), ",");
                    }

                    writer.writeNext(new String[]{fn, "None", String.valueOf(data.nonNull), (data.countsPerLevel.isEmpty() ? "" : String.valueOf(data.countsPerLevel.size())), vals});
                }

                valWriter.writeNext(new String[]{"Field", "Level", "Total"});
                valWriter.writeNext(new String[]{"Counts", "TotalVariants", String.valueOf(totalVariants)});
                valWriter.writeNext(new String[]{"Counts", "TotalPrivateVariants", String.valueOf(totalPrivateVariants)});
                valWriter.writeNext(new String[]{"Counts", "TotalSamples", String.valueOf(totalSubjects)});

                for (VariantContext.Type type : typeCounts.keySet())
                {
                    valWriter.writeNext(new String[]{"VariantType", type.name(), String.valueOf(typeCounts.get(type))});
                }

                for (String fn : new TreeSet<>(tracker.perField.keySet()))
                {
                    FieldData data = tracker.perField.get(fn);
                    for (String val : data.countsPerLevel.keySet())
                    {
                        valWriter.writeNext(new String[]{fn, val, String.valueOf(data.countsPerLevel.get(val))});
                    }
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        private void maybeWriteVariantLine(Set<List<String>> queuedLines, VariantContext vc, @Nullable String allele, String source, String reason, String description, Collection<String> overlappingGenes, Collection<String> omims, Collection<String> omimds, Logger log)
        {
            if (allele == null)
            {
                List<String> alts = new ArrayList<>();
                vc.getAlternateAlleles().forEach(a -> alts.add(a.getDisplayString()));
                allele = StringUtils.join(alts, ",");
            }

            Object af = null;
            if (allele != null && !allele.contains(",") && vc.hasAttribute("AF"))
            {
                List<Object> afs = vc.getAttributeAsList("AF");
                int i = 0;
                for (Allele a : vc.getAlternateAlleles())
                {
                    if (allele.equals(a.getBaseString()))
                    {
                        if (i < afs.size())
                        {
                            af = afs.get(i);
                            break;
                        }
                        else
                        {
                            log.error("alleles and AF values not same length for " + vc.getContig() + " " + vc.getStart() + ". " + vc.getAttributeAsString("AF", ""));
                        }
                    }

                    i++;
                }
            }

            queuedLines.add(Arrays.asList(vc.getContig(), String.valueOf(vc.getStart()), vc.getReference().getDisplayString(), allele, source, reason, description, StringUtils.join(overlappingGenes, ";"), StringUtils.join(omims, ";"), StringUtils.join(omimds, ";"), af == null ? "" : af.toString()));
        }

        private Map<String, String> parseSampleMap(File sampleMapFile) throws PipelineJobException
        {
            Map<String, String> ret = new HashMap<>();
            try (CSVReader reader = new CSVReader(Readers.getReader(sampleMapFile), '\t'))
            {
                String[] line;
                while ((line = reader.readNext()) != null)
                {
                    ret.put(line[0], line[1]);
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            return ret;
        }

        private String getPrimaryTrackName(File trackFile) throws PipelineJobException
        {
            Set<String> ret = new HashSet<>();
            try (CSVReader reader = new CSVReader(Readers.getReader(trackFile), '\t'))
            {
                String[] line;
                while ((line = reader.readNext()) != null)
                {
                    boolean isPrimary = ConvertHelper.convert(line[2], Boolean.class);
                    if (isPrimary)
                    {
                        ret.add(line[0]);
                    }
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            if (ret.size() != 1)
            {
                throw new PipelineJobException("Expected a single track labeled as primary: " + StringUtils.join(ret, ";"));
            }

            return ret.iterator().next();
        }

        private Map<String, List<String>> parseTrackMap(File trackFile) throws PipelineJobException
        {
            Map<String, List<String>> ret = new HashMap<>();
            try (CSVReader reader = new CSVReader(Readers.getReader(trackFile), '\t'))
            {
                String[] line;
                while ((line = reader.readNext()) != null)
                {
                    if (!ret.containsKey(line[0]))
                    {
                        ret.put(line[0], new ArrayList<>());
                    }

                    ret.get(line[0]).add(line[1]);
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            return ret;
        }

        private File renameSamples(File currentVCF, ReferenceGenome genome, JobContext ctx) throws PipelineJobException
        {
            ctx.getLogger().info("renaming samples in VCF");

            Set<String> allSamples = new HashSet<>();
            Map<String, List<String>> trackMap = parseTrackMap(getTrackFile(ctx.getSourceDirectory()));
            trackMap.forEach((k, v) -> allSamples.addAll(v));

            File outputFile = new File(currentVCF.getParentFile(), SequenceAnalysisService.get().getUnzippedBaseName(currentVCF.getName()) + ".renamed.vcf.gz");
            if (indexExists(outputFile))
            {
                ctx.getLogger().info("re-using existing output: " + outputFile.getPath());
            }
            else
            {
                Map<String, String> sampleMap = parseSampleMap(getSampleNameFile(ctx.getSourceDirectory()));

                VariantContextWriterBuilder builder = new VariantContextWriterBuilder();
                builder.setReferenceDictionary(SAMSequenceDictionaryExtractor.extractDictionary(genome.getSequenceDictionary()));
                builder.setOutputFile(outputFile);
                builder.setOption(Options.USE_ASYNC_IO);

                try (VCFFileReader reader = new VCFFileReader(currentVCF); VariantContextWriter writer = builder.build())
                {
                    VCFHeader header = reader.getFileHeader();
                    List<String> samples = header.getSampleNamesInOrder();
                    List<String> remappedSamples = new ArrayList<>();

                    for (String sample : samples)
                    {
                        if (sampleMap.containsKey(sample))
                        {
                            remappedSamples.add(sampleMap.get(sample));
                        }
                        else if (!allSamples.contains(sample))
                        {
                            ctx.getLogger().info("sample lacks an alias, but will not be included in output: " + sample);
                            remappedSamples.add(sample);
                        }
                        else
                        {
                            throw new PipelineJobException("No alternate name provided for sample: " + sample);
                        }
                    }

                    if (remappedSamples.size() != samples.size())
                    {
                        throw new PipelineJobException("The number of renamed samples does not equal starting samples: " + samples.size() + " / " + remappedSamples.size());
                    }

                    writer.writeHeader(new VCFHeader(header.getMetaDataInInputOrder(), remappedSamples));
                    try (CloseableIterator<VariantContext> it = reader.iterator())
                    {
                        while (it.hasNext())
                        {
                            writer.add(it.next());
                        }
                    }
                }
            }

            return outputFile;
        }

        private boolean indexExists(File vcf)
        {
            File idx = new File(vcf.getPath() + ".tbi");
            return idx.exists();
        }

        protected Set<String> parseOmim(String input, Logger log)
        {
            if (input == null)
            {
                return Collections.emptySet();
            }

            Set<String> ret = new LinkedHashSet<>();
            //TODO: consider: tokens = input.split("\\)(,){0,1}(?!_[0-9]+)");
            String[] tokens = input.split("\\)(,){0,1}");
            for (String token : tokens)
            {
                if (StringUtils.isEmpty(token))
                {
                    log.warn("OMIMD was empty: " + input + ", " + StringUtils.join(tokens, ";"));
                    continue;
                }

                String[] split = token.split("(_){0,1}\\([0-9]+$");

                //can occur if the string completely matches the delimiter
                if (split.length == 0)
                {
                    continue;
                }

                String id = "";
                String name = split[0];
                int lastDigits = lastIndexOfRegex(name, "\\d+$");
                if (lastDigits > 0)
                {
                    id = name.substring(lastDigits);
                    name = name.substring(0, lastDigits);
                }

                name = name.replaceAll("\\{", "");
                name = name.replaceAll("\\}", "");
                name = name.replaceAll("_", " ");
                name = StringUtils.trimToEmpty(name);
                name = name.replaceAll("^,", "");
                name = name.replaceAll(",$", "");
                name = name.replaceAll(" +", " ");
                name = name.replaceAll("\\[", "");
                name = name.replaceAll("\\]", "");
                name = name.replaceAll("^\\?", "");
                name = name.replaceAll("\\?$", "");
                name = StringUtils.trimToEmpty(name);

                if (!StringUtils.isEmpty(name) && !".".equals(name) && !StringUtils.isEmpty(id))
                {
                    ret.add(name + "<>" + id);
                }
            }

            return ret;
        }

        private int lastIndexOfRegex(String str, String toFind)
        {
            Pattern pattern = Pattern.compile(toFind);
            Matcher matcher = pattern.matcher(str);

            int lastIndex = -1;

            // Search for the given pattern
            while (matcher.find())
            {
                lastIndex = matcher.start();
            }

            return lastIndex;
        }
    }

    public static class TestCase extends Assert
    {
        private final Logger _log = Logger.getLogger(TestCase.class);

        @Test
        public void testOMIMParse() throws Exception
        {
            //chr01	9610198
            //chr01	65358095
            //chr01	76965588
            //chr04	32436641
            //chr01	5611371
            Set<Pair<String, Set<String>>> toTest = PageFlowUtil.set(
                    Pair.of("Spinal_muscular_atrophy,_distal,_autosomal_recessive,_4,_611067_(3),Charcot-Marie-Tooth_disease,_recessive_intermediate_C,_615376_(3)",PageFlowUtil.set("Spinal muscular atrophy, distal, autosomal recessive, 4<>611067", "Charcot-Marie-Tooth disease, recessive intermediate C<>615376")),
                    Pair.of("Charcot-Marie-Tooth_disease,_type_2A1,_118210_(3),_Pheochromocytoma,171300_(3),_{Neuroblastoma,_susceptibility_to,_1},_256700_(3)", PageFlowUtil.set("Charcot-Marie-Tooth disease, type 2A1<>118210","Pheochromocytoma<>171300","Neuroblastoma, susceptibility to, 1<>256700")),
                    Pair.of("Obesity,_morbid,_due_to_leptin_receptor_deficiency,_614963_(3)", PageFlowUtil.set("Obesity, morbid, due to leptin receptor deficiency<>614963")),
                    Pair.of("?Severe_combined_immunodeficiency_due_to_ADA_deficiency,_102700_(3),Adenosine_deaminase_deficiency,_partial,_102700_(3)", PageFlowUtil.set("Severe combined immunodeficiency due to ADA deficiency<>102700","Adenosine deaminase deficiency, partial<>102700")),
                    Pair.of("{Malaria,_cerebral,_susceptibility_to},_611162_(3),_{Septic_shock,susceptibility_to}_(3),_{Asthma,_susceptibility_to},_600807_(3),_{Dementia,_vascular,_susceptibility_to}_(3),_{Migraine_without_aura,_susceptibility_to},157300_(3)_157300_(3)", PageFlowUtil.set("Malaria, cerebral, susceptibility to<>611162","Asthma, susceptibility to<>600807","Migraine without aura, susceptibility to<>157300"))
                    //TODO: this is a badly formed entry; however, it will not get parsed correctly.  they key thing is that we get the entityId right though
                    //Pair.of("Peroxisome_biogenesis_disorder_6A_(Zellweger),_614870_(3),Peroxisome_biogenesis_disorder_6B,_614871_(3)", PageFlowUtil.set("Peroxisome_biogenesis_disorder_6A_(Zellweger)<>614870", "Peroxisome_biogenesis_disorder_6B<>614871"))
                    //Night_blindness,_congenital_stationary_(complete),_1F,_autosomalrecessive,_615058_(3)
            );

            PublicReleaseHandler.Processor pr = new PublicReleaseHandler.Processor();

            for (Pair<String, Set<String>> pair : toTest)
            {
                Set<String> ret = pr.parseOmim(pair.getLeft(), _log);
                Assert.assertEquals(pair.getRight(), ret);

                //NOTE: since this requires an API key and queries OMIM not suitable to general testing, but this can be uncommented for local dev
                //for (String term : ret)
                //{
                //    String updated = pr.updateOmimD(term, ContainerManager.getRoot(), _log);
                //    Assert.assertNotNull(updated);
                //}
            }
        }
    }
}
