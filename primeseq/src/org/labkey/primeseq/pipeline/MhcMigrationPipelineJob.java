package org.labkey.primeseq.pipeline;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Results;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.WorkbookContainerType;
import org.labkey.api.di.DataIntegrationService;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileUrls;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.query.Filter;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class MhcMigrationPipelineJob extends PipelineJob
{
    private String remoteServerFolder;
    private String remoteConnectionName;

    private Container targetContainer;

    public static class Provider extends PipelineProvider
    {
        public static final String NAME = "mhcMigrationPipeline";

        public Provider(Module owningModule)
        {
            super(NAME, owningModule);
        }

        @Override
        public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
        {

        }
    }

    // Default constructor for serialization
    protected MhcMigrationPipelineJob()
    {
    }

    public MhcMigrationPipelineJob(Container c, User u, ActionURL url, PipeRoot pipeRoot, String remoteConnectionName, String remoteServerFolder)
    {
        super(Provider.NAME, new ViewBackgroundInfo(c, u, url), pipeRoot);

        this.targetContainer = c;
        this.remoteConnectionName = remoteConnectionName;
        this.remoteServerFolder = remoteServerFolder;

        File subdir = new File(pipeRoot.getRootPath(), Provider.NAME);
        if (!subdir.exists())
        {
            subdir.mkdirs();
        }

        setLogFile(new File(subdir, FileUtil.makeFileNameWithTimestamp("mhcMigration", "log")));

    }

    @Override
    public ActionURL getStatusHref()
    {
        return PageFlowUtil.urlProvider(FileUrls.class).urlBegin(getContainer());
    }

    @Override
    public String getDescription()
    {
        return "Migrate MHC Data";
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(MhcMigrationPipelineJob.class));
    }

    public static class Task extends PipelineJob.Task<Task.Factory>
    {
        protected Task(Factory factory, PipelineJob job)
        {
            super(factory, job);
        }

        public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
        {
            public Factory()
            {
                super(Task.class);
            }

            @Override
            public List<FileType> getInputTypes()
            {
                return Collections.emptyList();
            }

            @Override
            public String getStatusName()
            {
                return PipelineJob.TaskStatus.running.toString();
            }

            @Override
            public List<String> getProtocolActionNames()
            {
                return Arrays.asList("Migrate MHC Data");
            }

            @Override
            public PipelineJob.Task createTask(PipelineJob job)
            {
                return new Task(this, job);
            }

            @Override
            public boolean isJobComplete(PipelineJob job)
            {
                return false;
            }
        }

        private MhcMigrationPipelineJob getPipelineJob()
        {
            return (MhcMigrationPipelineJob)getJob();
        }

        private Connection getConnection()
        {
            DataIntegrationService.RemoteConnection rc = DataIntegrationService.get().getRemoteConnection(getPipelineJob().remoteConnectionName, getPipelineJob().targetContainer, getJob().getLogger());

            return(rc.connection);
        }

        @Override
        public RecordedActionSet run() throws PipelineJobException
        {
            try (DbScope.Transaction transaction = DbScope.getLabKeyScope().ensureTransaction())
            {
                createWorkbooks();
                transaction.commitAndKeepConnection();

                replaceEntireTable("laboratory", "samples", Arrays.asList("samplename", "subjectid", "sampledate", "sampletype", "samplesubtype", "samplesource", "location", "freezer", "cane", "box", "box_row", "box_column", "comment", "workbook/workbookId", "samplespecies", "processdate", "concentration", "concentration_units", "quantity", "quantity_units", "ratio"), "workbook/workbookId", true, getJob().getContainer(), getPipelineJob().remoteServerFolder);
                replaceEntireTable("laboratory", "subjects", Arrays.asList("subjectname", "species"), null, true, getJob().getContainer(), getPipelineJob().remoteServerFolder);
                transaction.commitAndKeepConnection();

                createLibraries();
                createLibraryMembers();
                transaction.commitAndKeepConnection();

                createReadsets();
                transaction.commitAndKeepConnection();

                createReaddata();
                transaction.commitAndKeepConnection();

                createAnalyses();
                createOutputFiles();
                transaction.commitAndKeepConnection();

                createQualityMetrics();
                transaction.commitAndKeepConnection();

                //create assay runs, including data and haplotypes
                syncAssay("GenotypeAssay", "Genotype", Arrays.asList("RowId", "Name", "Comments", "performedBy", "runDate", "instrument", "assayType", "barcode"), Arrays.asList("subjectId", "date", "marker", "result", "qual_result", "sampleId", "category", "plate", "well", "parentId", "comment", "requestid", "qcflag", "analysisId", "DataId", "sampleType", "statusflag", "rawResult"));
                transaction.commitAndKeepConnection();

                syncAssay("SSP_assay", "SSP", Arrays.asList("RowId", "Name", "Comments", "performedBy", "runDate"), Arrays.asList("subjectId", "date", "laneNumber", "method", "sampleType", "primerPair", "result", "comment", "qcflag", "statusflag"));
                transaction.commitAndKeepConnection();

                createAlignmentSummary();

                transaction.commit();
            }

            return new RecordedActionSet();
        }

        private void syncAssay(String providerName, String assayName, List<String> runColumns, List<String> resultColumns) throws PipelineJobException
        {
            getJob().getLogger().info("syncing assay: " + providerName + " / " + assayName);
            AssayProvider ap = AssayService.get().getProvider(providerName);
            for (Integer wb : workbookMap.keySet())
            {
                getJob().getLogger().info("processing workbook: " + wb);

                List<ExpProtocol> protocols = AssayService.get().getAssayProtocols(workbookMap.get(wb), ap);
                ExpProtocol protocol = protocols.get(0);

                SelectRowsCommand sr1 = new SelectRowsCommand("assay." + ap.getName() + "." + protocol.getName(), "Runs");
                sr1.setColumns(runColumns);

                try
                {
                    SelectRowsResponse srr = sr1.execute(getConnection(), getPipelineJob().remoteServerFolder + wb);
                    if (srr.getRowCount().intValue() == 0)
                    {
                        continue;
                    }

                    //Existing runs:
                    List<String> existingRunNames = new TableSelector(AssayService.get().createRunTable(protocol, ap, getJob().getUser(), workbookMap.get(wb), null), PageFlowUtil.set("Name")).getArrayList(String.class);
                    if (existingRunNames.size() == srr.getRowCount().intValue())
                    {
                        getJob().getLogger().info("Run count matches, skipping: " + wb);
                        continue;
                    }

                    File assayTmp = File.createTempFile("assay-upload", ".txt").getAbsoluteFile();
                    ViewBackgroundInfo info = getJob().getInfo();
                    ViewContext vc = ViewContext.getMockViewContext(info.getUser(), workbookMap.get(wb), info.getURL(), false);
                    final Set<Object> missingAnalyses = new HashSet<>();
                    srr.getRows().forEach(run -> {
                        if (existingRunNames.contains(run.get("Name")))
                        {
                            getJob().getLogger().info("Run exists, skipping: " + run.get("Name"));
                            return;
                        }

                        JSONObject json = new JSONObject();
                        json.put("Run", run);

                        SelectRowsCommand sr2 = new SelectRowsCommand("assay." + ap.getName() + "." + protocol.getName(), "Data");
                        sr2.setColumns(resultColumns);
                        sr2.addFilter(new Filter("Run", run.get("RowId")));

                        try
                        {
                            SelectRowsResponse srr2 = sr2.execute(getConnection(), getPipelineJob().remoteServerFolder + wb);
                            List<Map<String, Object>> resultRows = srr2.getRows();
                            if (resultRows.isEmpty())
                            {
                                getJob().getLogger().info("No results, skipping: " + run.get("Name"));
                                return;
                            }

                            resultRows.forEach(x -> {
                                if (x.get("analysisId") != null)
                                {
                                    if (analysisMap.containsKey(x.get("analysisId")))
                                    {
                                        x.put("analysisId", analysisMap.get(x.get("analysisId")));
                                    }
                                    else
                                    {
                                        if (!missingAnalyses.contains(x.get("analysisId")))
                                        {
                                            getJob().getLogger().error("Unable to find analysis to match: " + x.get("analysisId"));
                                            missingAnalyses.add(x.get("analysisId"));
                                        }
                                    }
                                }
                            });

                            LaboratoryService.get().saveAssayBatch(resultRows, json, assayTmp, vc, ap, protocol);
                        }
                        catch (ValidationException | CommandException | IOException e)
                        {
                            throw new RuntimeException(e);
                        }
                    });

                }
                catch (IOException | CommandException e)
                {
                    throw new PipelineJobException(e);
                }
            }
        }

        private void createQualityMetrics() throws PipelineJobException
        {
            for (int workbook : workbookMap.keySet())
            {
                replaceEntireTable("sequenceanalysis", "quality_metrics", Arrays.asList("dataid", "dataid/DatafileUrl", "dataid/Name", "runid/JobId/FilePath", "category", "metricname", "metricvalue", "qualvalue", "analysis_id", "readset", "readset/runid/JobId", "readset/runid/JobId/FilePath", "dataid/Run/JobId/FilePath"), null, true, workbookMap.get(workbook), getPipelineJob().remoteServerFolder + workbook + "/");
            }
        }

        private void createAlignmentSummary() throws PipelineJobException
        {
            try
            {
                TableInfo alignmentSummary = DbSchema.get("sequenceanalysis", DbSchemaType.Module).getTable("alignment_summary");
                TableInfo alignmentSummaryJunction = DbSchema.get("sequenceanalysis", DbSchemaType.Module).getTable("alignment_summary_junction");

                //NOTE: split by workbook to avoid huge API calls:
                SelectRowsCommand srWB = new SelectRowsCommand("core", "workbooks");
                srWB.setColumns(Arrays.asList("Name"));

                SelectRowsResponse srrWB = srWB.execute(getConnection(), getPipelineJob().remoteServerFolder);
                List<Object> workbooks = srrWB.getRows().stream().map(x -> x.get("Name")).collect(Collectors.toList());
                for (Object workbook : workbooks)
                {
                    getJob().getLogger().info("importing alignments for workbook: " + workbook);

                    SelectRowsCommand sr = new SelectRowsCommand("sequenceanalysis", "alignment_summary");
                    sr.setColumns(Arrays.asList("rowid", "analysis_id", "file_id", "total", "total_forward", "total_reverse", "valid_pairs", "workbook/workbookId"));

                    SelectRowsResponse srr = sr.execute(getConnection(), getPipelineJob().remoteServerFolder + workbook + "/");
                    getJob().getLogger().info("total alignment_summary records: " + srr.getRowCount());
                    if (srr.getRowCount().intValue() == 0)
                    {
                        continue;
                    }

                    long existing = new TableSelector(alignmentSummary, new SimpleFilter(FieldKey.fromString("container"), workbookMap.get(workbook).getId()), null).getRowCount();
                    if (srr.getRowCount().longValue() == existing)
                    {
                        getJob().getLogger().info("alignment_summary row count identical, skipping: " + workbook);
                        continue;
                    }

                    final Map<Integer, Integer> alignmentSummaryMap = new HashMap<>(srr.getRowCount().intValue());
                    srr.getRowset().forEach(rs -> {
                        CaseInsensitiveHashMap<Object> map = new CaseInsensitiveHashMap<>();
                        Integer localId = analysisMap.get(rs.getValue("analysis_id"));
                        if (localId == null)
                        {
                            throw new RuntimeException("Unable to find analysis: " + rs.getValue("analysis_id"));
                        }
                        map.put("analysis_id", localId);
                        map.put("file_id", analysisToFileMap.get(localId));

                        map.put("total", rs.getValue("total"));
                        map.put("total_forward", rs.getValue("total_forward"));
                        map.put("total_reverse", rs.getValue("total_reverse"));
                        map.put("valid_pairs", rs.getValue("valid_pairs"));

                        Container c = workbookMap.get((int) rs.getValue("workbook/workbookId"));
                        map.put("container", c.getId());

                        map = Table.insert(getJob().getUser(), alignmentSummary, map);
                        if (map.get("rowid") == null)
                        {
                            throw new RuntimeException("RowId was null after insert!");
                        }

                        alignmentSummaryMap.put((int) rs.getValue("rowid"), (int) map.get("rowid"));
                    });

                    SelectRowsCommand sr2 = new SelectRowsCommand("sequenceanalysis", "alignment_summary_junction");
                    sr2.setColumns(Arrays.asList("analysis_id", "alignment_id", "ref_nt_id", "status", "analysis_id/workbook/workbookId", "ref_nt_id/name"));
                    sr2.addFilter(new Filter("analysis_id/workbook/workbookId", workbook));

                    SelectRowsResponse srr2 = sr2.execute(getConnection(), getPipelineJob().remoteServerFolder + workbook + "/");
                    getJob().getLogger().info("total alignment_summary_junction records: " + srr2.getRowCount());
                    srr2.getRowset().forEach(rs -> {
                        CaseInsensitiveHashMap<Object> map = new CaseInsensitiveHashMap<>();
                        Integer localId = analysisMap.get(rs.getValue("analysis_id"));
                        if (localId == null)
                        {
                            throw new RuntimeException("Unable to find analysis: " + rs.getValue("analysis_id"));
                        }
                        map.put("analysis_id", localId);

                        Integer localNT = sequenceMap.get(rs.getValue("ref_nt_id"));
                        if (localNT == null)
                        {
                            throw new RuntimeException("Unable to find ref_nt_id: " + rs.getValue("ref_nt_id") + " / " + rs.getValue("ref_nt_id/name"));
                        }
                        map.put("ref_nt_id", localNT);
                        map.put("status", rs.getValue("status"));

                        map.put("alignment_id", alignmentSummaryMap.get("alignment_id"));

                        Container c = workbookMap.get((int) rs.getValue("analysis_id/workbook/workbookId"));
                        map.put("container", c.getId());

                        Table.insert(getJob().getUser(), alignmentSummaryJunction, map);
                    });
                }
            }
            catch (CommandException | IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        private void replaceEntireTable(String schema, String query, List<String> columns, String workbookColName, boolean truncateExisting, Container targetContainer, String remoteServerFolder) throws PipelineJobException
        {
            getJob().getLogger().info("replacing table: " + query + " for container: " + targetContainer.getPath());
            try
            {
                SelectRowsCommand sr = new SelectRowsCommand(schema, query);
                sr.setColumns(columns);
                SelectRowsResponse srr = sr.execute(getConnection(), remoteServerFolder);

                TableInfo ti = QueryService.get().getUserSchema(getJob().getUser(), targetContainer, schema).getTable(query);
                long existing = new TableSelector(ti).getRowCount();
                if (srr.getRowCount().longValue() == existing)
                {
                    getJob().getLogger().info("Row counts identical, assuming has been synced: " + query);
                    return;
                }
                else if (srr.getRowCount().equals(0))
                {
                    getJob().getLogger().info("No rows, skipping: " + query);
                    return;
                }

                List<Map<String, Object>> toInsert = new ArrayList<>();
                srr.getRowset().forEach(r -> {
                    Map<String, Object> row = new CaseInsensitiveHashMap<>();
                    srr.getColumnModel().forEach(col -> {
                        String colName = (String) col.get("dataIndex");
                        if (ti.getColumn(colName) != null)
                        {
                            Object val = r.getValue(colName);
                            if ("readset".equals(colName) || "readsetid".equals(colName))
                            {
                                if (val != null && !readsetMap.containsKey(val))
                                {
                                    throw new IllegalStateException("Unable to find readset: " + val);
                                }

                                val = readsetMap.get(val);
                            }
                            else if ("library_id".equals(colName))
                            {
                                if (val != null && !libraryMap.containsKey(val))
                                {
                                    throw new IllegalStateException("Unable to find library: " + val);
                                }

                                val = libraryMap.get(val);

                            }
                            else if ("ref_nt_id".equals(colName))
                            {
                                if (val != null && !sequenceMap.containsKey(val))
                                {
                                    throw new IllegalStateException("Unable to find sequence: " + val);
                                }

                                val = sequenceMap.get(val);
                            }
                            else if ("analysis_id".equals(colName))
                            {
                                if (val != null && !analysisMap.containsKey(val))
                                {
                                    throw new IllegalStateException("Unable to find analysis: " + val);
                                }

                                val = analysisMap.get(val);
                            }

                            row.put(colName, val);
                        }
                        else if ("dataid/DatafileUrl".equalsIgnoreCase(colName) && r.getValue("dataid/DatafileUrl") != null)
                        {
                            String remoteJobRoot;
                            if (r.getValue("runid/JobId/FilePath") == null)
                            {
                                if (r.getValue("analysis_id") != null)
                                {
                                    Integer localAnalysisId = analysisMap.get((int) r.getValue("analysis_id"));
                                    if (analysisToJobPath.containsKey(localAnalysisId))
                                    {
                                        remoteJobRoot = analysisToJobPath.get(localAnalysisId);
                                    }
                                    else
                                    {
                                        getJob().getLogger().error("Missing path: " + r.getValue("dataid/DatafileUrl"));
                                        return;
                                    }
                                }
                                else if (r.getValue("readset/runid/JobId/FilePath") != null)
                                {
                                    remoteJobRoot = getParent(URI.create(String.valueOf(r.getValue("readset/runid/JobId/FilePath")).replaceAll(" ", "%20")).getPath());
                                }
                                else if (r.getValue("dataid/Run/JobId/FilePath") != null)
                                {
                                    remoteJobRoot = getParent(URI.create(String.valueOf(r.getValue("dataid/Run/JobId/FilePath")).replaceAll(" ", "%20")).getPath());
                                }
                                else
                                {
                                    getJob().getLogger().error("Missing path: " + r.getValue("dataid/DatafileUrl"));
                                    return;
                                }
                            }
                            else
                            {
                                remoteJobRoot = getParent(URI.create(String.valueOf(r.getValue("runid/JobId/FilePath")).replaceAll(" ", "%20")).getPath());
                            }

                            if (remoteJobRoot != null)
                            {
                                URI localFileRoot = PipelineService.get().getPipelineRootSetting(targetContainer).getRootPath().toURI();
                                URI localPath = translateURI(String.valueOf(r.getValue("dataid/DatafileUrl")), remoteJobRoot, localFileRoot.getPath());
                                row.put("dataid", getOrCreateExpData(localPath, targetContainer, String.valueOf(r.getValue("dataid/Name"))));
                            }
                            else
                            {
                                getJob().getLogger().error("Unable to find job root: " + r.getValue("dataid/DatafileUrl"));
                                return;
                            }
                        }
                    });

                    if (workbookColName != null)
                    {
                        Object workbookId = r.getValue(workbookColName);
                        if (workbookId != null)
                        {
                            row.put("container", workbookMap.get(Integer.parseInt(String.valueOf(workbookId))).getId());
                        }
                    }

                    toInsert.add(row);
                });

                if (truncateExisting)
                {
                    List<Object> toDelete = new TableSelector(ti, new HashSet<>(ti.getPkColumnNames())).getArrayList(Object.class);
                    if (!toDelete.isEmpty())
                    {
                        final List<Map<String, Object>> rowsToDelete = new ArrayList<>();
                        toDelete.forEach(x -> {
                            Map<String, Object> map = new CaseInsensitiveHashMap<>();
                            map.put(ti.getPkColumnNames().get(0), x);
                            rowsToDelete.add(map);
                        });

                        ti.getUpdateService().deleteRows(getJob().getUser(), targetContainer, rowsToDelete, null, null);
                    }
                }

                BatchValidationException bve = new BatchValidationException();
                ti.getUpdateService().insertRows(getJob().getUser(), targetContainer, toInsert, bve, null, null);
                if (bve.hasErrors())
                {
                    throw bve;
                }
            }
            catch (Exception e)
            {
                throw new PipelineJobException(e);
            }
        }

        //All of these map remote Id to local Id
        private final Map<Integer, Container> workbookMap = new HashMap<>();
        private final Map<Integer, Integer> readsetMap = new HashMap<>();
        private final Map<Integer, Integer> readdataMap = new HashMap<>();
        private final Map<Integer, Integer> analysisMap = new HashMap<>();
        private final Map<Integer, Integer> analysisToFileMap = new HashMap<>(); //local analysis_id -> alignment file
        private final Map<Integer, String> analysisToJobPath = new HashMap<>();
        private final Map<Integer, Integer> libraryMap = new HashMap<>();
        private final Map<Integer, Integer> outputFileMap = new HashMap<>();
        private final Map<Integer, Integer> sequenceMap = new HashMap<>();
        private final Map<Integer, Integer> runIdMap = new HashMap<>();
        private final Map<Integer, Integer> jobIdMap = new HashMap<>();

        private void createLibraryMembers()
        {
            getJob().getLogger().info("Creating library members");

            final UserSchema us = QueryService.get().getUserSchema(getJob().getUser(), getPipelineJob().targetContainer, "sequenceanalysis");
            final TableInfo ti = us.getTable("reference_library_members");
            final TableInfo refNtTable = us.getTable("ref_nt_sequences");

            try
            {
                SelectRowsCommand sr = new SelectRowsCommand("sequenceanalysis", "reference_library_members");
                sr.setColumns(Arrays.asList("rowid", "library_id", "ref_nt_id", "ref_nt_id/name", "ref_nt_id/seqLength", "workbook/workbookId"));

                SelectRowsResponse srr = sr.execute(getConnection(), getPipelineJob().remoteServerFolder);

                List<Map<String, Object>> sequencesToCreate = new ArrayList<>();
                srr.getRowset().forEach(rd -> {
                    int seqLength = Integer.parseInt(String.valueOf(rd.getValue("ref_nt_id/seqLength")));

                    int remoteSeqId = Integer.parseInt(String.valueOf(rd.getValue("ref_nt_id")));
                    String name = String.valueOf(rd.getValue("ref_nt_id/name"));

                    //Skip all pigtail MHC.
                    if (name.startsWith("Mane"))
                    {
                        return;
                    }

                    int localSeqId = getOrCreateSequence(remoteSeqId, name, seqLength, refNtTable);
                    if (localSeqId == -1)
                    {
                        return;
                    }

                    int remoteLibraryId = Integer.parseInt(String.valueOf(rd.getValue("library_id")));
                    Integer localLibraryId = libraryMap.get(remoteLibraryId);
                    if (localLibraryId == null)
                    {
                        throw new IllegalStateException("Unable to find library id: " + remoteLibraryId);
                    }

                    SimpleFilter filter = new SimpleFilter(FieldKey.fromString("library_id"), localLibraryId);
                    filter.addCondition(FieldKey.fromString("ref_nt_id"), localSeqId);

                    if (new TableSelector(ti, PageFlowUtil.set("rowid"), filter, null).exists())
                    {
                        //Already exists:
                        return;
                    }

                    Map<String, Object> toCreate = new CaseInsensitiveHashMap<>();
                    toCreate.put("library_id", localLibraryId);
                    toCreate.put("ref_nt_id", localSeqId);
                    sequencesToCreate.add(toCreate);
                });

                getJob().getLogger().info("Total sequences to create: " + sequencesToCreate.size());
                if (!sequencesToCreate.isEmpty())
                {
                    BatchValidationException bve = new BatchValidationException();
                    List<Map<String, Object>> created = ti.getUpdateService().insertRows(getJob().getUser(), getPipelineJob().targetContainer, sequencesToCreate, bve, null, null);
                    if (bve.hasErrors())
                    {
                        throw new RuntimeException(bve);
                    }
                }
            }
            catch (Exception e)
            {
                getJob().getLogger().error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }

        private int getOrCreateSequence(int remoteSeqId, String name, int seqLength, TableInfo refNtTable)
        {
            if (sequenceMap.containsKey(remoteSeqId))
            {
                return sequenceMap.get(remoteSeqId);
            }
            else
            {
                SimpleFilter filter = new SimpleFilter(FieldKey.fromString("name"), name);
                filter.addCondition(FieldKey.fromString("datedisabled"), null, CompareType.ISBLANK);
                TableSelector ts = new TableSelector(refNtTable, PageFlowUtil.set("rowid", "seqLength"), filter, new Sort("rowid"));
                if (ts.exists())
                {
                    if (ts.getRowCount() > 1)
                    {
                        getJob().getLogger().info("Duplicate ref name: " + name);
                    }

                    AtomicInteger localId = new AtomicInteger(-1);
                    ts.forEachResults(rs -> {
                        if (rs.getInt(FieldKey.fromString("seqLength")) < seqLength)
                        {
                            //NOTE: accept these as most are trimmed
                            getJob().getLogger().warn("length doesnt match for " + name + ", expected: " + seqLength + ", was: " + rs.getInt(FieldKey.fromString("seqLength")));
                        }

                        localId.set(rs.getInt(FieldKey.fromString("rowid")));
                    });

                    if (localId.get() != -1)
                    {
                        sequenceMap.put(remoteSeqId, localId.get());
                        return localId.get();
                    }
                }

                getJob().getLogger().error("Sequence missing: " + name);
                return -1;
            }
        }

        public String getParent(String path)
        {
            final char separatorChar = '/';

            int index = path.lastIndexOf(separatorChar);
            if (index == -1)
            {
                throw new IllegalArgumentException("Missing slash");
            }

            return path.substring(0, index);
        }

        private void createLibraries()
        {
            getJob().getLogger().info("Creating libraries");
            try
            {
                final TableInfo libraryTable = QueryService.get().getUserSchema(getJob().getUser(), getPipelineJob().targetContainer, "sequenceanalysis").getTable("reference_libraries");

                SelectRowsCommand sr = new SelectRowsCommand("sequenceanalysis", "reference_libraries");
                sr.setColumns(Arrays.asList("rowid", "name", "description", "fasta_file", "datedisabled", "assemblyId", "fasta_file/DataFileUrl", "fasta_file/Name", "workbook/workbookId"));

                SelectRowsResponse srr = sr.execute(getConnection(), getPipelineJob().remoteServerFolder);

                srr.getRowset().forEach(rd -> {
                    int remoteId = Integer.parseInt(String.valueOf(rd.getValue("rowid")));

                    Integer remoteWorkbook = rd.getValue("workbook/workbookId") == null ? null : Integer.parseInt(String.valueOf(rd.getValue("workbook/workbookId")));
                    Container targetContainer = remoteWorkbook == null ? getPipelineJob().targetContainer : workbookMap.get(remoteWorkbook);

                    SimpleFilter filter = new SimpleFilter(FieldKey.fromString("name"), rd.getValue("name"));
                    TableSelector ts = new TableSelector(libraryTable, PageFlowUtil.set("rowid"), filter, null);
                    if (ts.exists())
                    {
                        libraryMap.put(remoteId, ts.getObject(Integer.class));
                    }
                    else
                    {
                        Map<String, Object> toCreate = new CaseInsensitiveHashMap<>();
                        toCreate.put("name", rd.getValue("name"));
                        toCreate.put("description", rd.getValue("description"));
                        toCreate.put("datedisabled", rd.getValue("datedisabled"));
                        toCreate.put("assemblyId", rd.getValue("assemblyId"));
                        try
                        {
                            String remoteJobRoot = getParent(URI.create(String.valueOf(rd.getValue("fasta_file/DatafileUrl"))).getPath());
                            URI localJobRoot = PipelineService.get().getPipelineRootSetting(targetContainer).getRootPath().toURI();
                            URI localFasta = translateURI(String.valueOf(rd.getValue("fasta_file/DatafileUrl")), remoteJobRoot, localJobRoot.getPath());
                            toCreate.put("fasta_file", getOrCreateExpData(localFasta, targetContainer, String.valueOf(rd.getValue("fasta_file/Name"))));

                            //Ensure parent folder exists:
                            File localJobRootFile = new File(localFasta).getParentFile();
                            if (!localJobRootFile.getParentFile().exists())
                            {
                                localJobRootFile.getParentFile().mkdirs();
                            }

                            getJob().getLogger().info(remoteJobRoot);
                            getJob().getLogger().info(localJobRoot.getPath());
                            File remoteJobRootFile = new File(remoteJobRoot);
                            if (remoteJobRootFile.exists())
                            {
                                if (!localJobRootFile.exists())
                                {
                                    throw new PipelineJobException("Expected folder to have been copied: " + remoteJobRootFile.getPath() + " to " + localJobRootFile.getPath());
                                }
                            }

                            BatchValidationException bve = new BatchValidationException();
                            List<Map<String, Object>> created = libraryTable.getUpdateService().insertRows(getJob().getUser(), getPipelineJob().targetContainer, Arrays.asList(toCreate), bve, null, null);
                            if (bve.hasErrors())
                            {
                                throw new RuntimeException(bve);
                            }

                            libraryMap.put(remoteId, Integer.parseInt(String.valueOf(created.get(0).get("rowid"))));
                        }
                        catch (Exception e)
                        {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }
            catch (Exception e)
            {
                getJob().getLogger().error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }

        private void createOutputFiles()
        {
            getJob().getLogger().info("Creating outputfiles");
            try
            {
                final TableInfo outputTable = QueryService.get().getUserSchema(getJob().getUser(), getPipelineJob().targetContainer, "sequenceanalysis").getTable("outputfiles");

                SelectRowsCommand sr = new SelectRowsCommand("sequenceanalysis", "outputfiles");
                sr.setColumns(Arrays.asList("rowid", "name", "description", "dataid", "library_id", "readset", "analysis_id", "category", "sra_accession", "dataid/DataFileUrl", "dataid/Name", "runid", "runid/JobId", "runid/Name", "workbook/workbookId", "runid/Name", "runid/JobId/FilePath"));

                SelectRowsResponse srr = sr.execute(getConnection(), getPipelineJob().remoteServerFolder);

                srr.getRowset().forEach(rd -> {
                    int remoteId = Integer.parseInt(String.valueOf(rd.getValue("rowid")));
                    int remoteReadset = Integer.parseInt(String.valueOf(rd.getValue("readset")));
                    Integer localReadset = readsetMap.get(remoteReadset);
                    if (localReadset == null)
                    {
                        throw new IllegalArgumentException("Unable to find readset for remote id: " + remoteReadset);
                    }

                    int remoteLibrary = Integer.parseInt(String.valueOf(rd.getValue("library_id")));
                    Integer localLibrary = libraryMap.get(remoteLibrary);
                    if (localLibrary == null)
                    {
                        throw new IllegalArgumentException("Unable to find genome for remote id: " + remoteLibrary);
                    }

                    Integer localAnalysis;
                    if (rd.getValue("analysis_id") != null)
                    {
                        int remoteAnalysis = Integer.parseInt(String.valueOf(rd.getValue("analysis_id")));
                        localAnalysis = analysisMap.get(remoteAnalysis);
                        if (localAnalysis == null)
                        {
                            throw new IllegalArgumentException("Unable to find analysis for remote id: " + remoteAnalysis);
                        }
                    }
                    else
                    {
                        localAnalysis = null;
                    }

                    Readset rs = SequenceAnalysisService.get().getReadset(localReadset, getJob().getUser());
                    Container targetWorkbook = ContainerManager.getForId(rs.getContainer());

                    SimpleFilter filter = new SimpleFilter(FieldKey.fromString("readset"), rs.getRowId());
                    filter.addCondition(FieldKey.fromString("name"), rd.getValue("name"));
                    filter.addCondition(FieldKey.fromString("category"), rd.getValue("category"));
                    filter.addCondition(FieldKey.fromString("analysis_id"), localAnalysis);
                    filter.addCondition(FieldKey.fromString("container"), targetWorkbook.getId(), CompareType.EQUAL);

                    TableSelector tsOutputFiles = new TableSelector(outputTable, PageFlowUtil.set("rowid"), filter, null);
                    if (tsOutputFiles.exists())
                    {
                        outputFileMap.put(remoteId, tsOutputFiles.getObject(Integer.class));
                    }
                    else
                    {
                        Map<String, Object> toCreate = new CaseInsensitiveHashMap<>();
                        toCreate.put("readset", rs.getRowId());
                        toCreate.put("analysis_id", localAnalysis);
                        toCreate.put("description", rd.getValue("description"));
                        toCreate.put("sra_accession", rd.getValue("sra_accession"));
                        toCreate.put("library_id", localLibrary);
                        toCreate.put("name", rd.getValue("name"));
                        toCreate.put("category", rd.getValue("category"));

                        try
                        {
                            if (rd.getValue("runid/JobId") == null)
                            {
                                throw new PipelineJobException("Output missing runId");
                            }

                            int remoteJobId = Integer.parseInt(String.valueOf(rd.getValue("runid/JobId")));
                            int jobId = getOrCreateJob(remoteJobId, targetWorkbook);
                            PipelineStatusFile sf = PipelineService.get().getStatusFile(jobId);

                            String localJobRoot = getParent(sf.getFilePath());
                            String remoteJobRoot = getParent(URI.create(String.valueOf(rd.getValue("runid/JobId/FilePath")).replaceAll(" ", "%20")).getPath());

                            URI newFileAlignment = translateURI(String.valueOf(rd.getValue("dataid/DatafileUrl")), remoteJobRoot, localJobRoot);
                            toCreate.put("dataid", getOrCreateExpData(newFileAlignment, targetWorkbook, String.valueOf(rd.getValue("dataid/Name"))));

                            //Create run:
                            if (rd.getValue("runid") != null && rd.getValue("runid/JobId") != null)
                            {
                                int runId = createExpRun(Integer.parseInt(String.valueOf(rd.getValue("runid"))), targetWorkbook, String.valueOf(rd.getValue("runid/Name")), jobId);
                                toCreate.put("runid", runId);
                            }
                            else
                            {
                                getJob().getLogger().error("output missing runid: " + remoteId);
                            }

                            BatchValidationException bve = new BatchValidationException();
                            List<Map<String, Object>> created = outputTable.getUpdateService().insertRows(getJob().getUser(), getPipelineJob().targetContainer, Arrays.asList(toCreate), bve, null, null);
                            if (bve.hasErrors())
                            {
                                throw new RuntimeException(bve);
                            }

                            outputFileMap.put(remoteId, Integer.parseInt(String.valueOf(created.get(0).get("rowid"))));
                        }
                        catch (Exception e)
                        {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }
            catch (Exception e)
            {
                getJob().getLogger().error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }

        private void createAnalyses()
        {
            getJob().getLogger().info("Creating analyses");
            try
            {
                final TableInfo analysisTable = QueryService.get().getUserSchema(getJob().getUser(), getPipelineJob().targetContainer, "sequenceanalysis").getTable("sequence_analyses");

                SelectRowsCommand sr = new SelectRowsCommand("sequenceanalysis", "sequence_analyses");
                sr.setColumns(Arrays.asList("rowid", "type", "description", "synopsis", "runid", "readset", "alignmentfile", "reference_library", "library_id", "sra_accession", "alignmentfile/DataFileUrl", "alignmentfile/Name", "alignmentfile/Name", "reference_library", "reference_library/DataFileUrl", "reference_library/Name", "runid/jobid", "runid/Name", "workbook/workbookId", "runid/JobId", "runid/Name", "runid/JobId/FilePath", "runid/JobId/Description"));

                SelectRowsResponse srr = sr.execute(getConnection(), getPipelineJob().remoteServerFolder);

                srr.getRowset().forEach(rd -> {
                    int remoteId = Integer.parseInt(String.valueOf(rd.getValue("rowid")));
                    if (rd.getValue("readset") == null)
                    {
                        getJob().getLogger().warn("analysis lacks readset, skipping: " + remoteId);
                        return;
                    }

                    int remoteReadset = Integer.parseInt(String.valueOf(rd.getValue("readset")));
                    Integer localReadset = readsetMap.get(remoteReadset);
                    if (localReadset == null)
                    {
                        throw new IllegalArgumentException("Unable to find readset for remote id: " + remoteReadset);
                    }

                    Integer localLibrary = null;
                    if (rd.getValue("library_id") != null)
                    {
                        int remoteLibrary = Integer.parseInt(String.valueOf(rd.getValue("library_id")));
                        localLibrary = libraryMap.get(remoteLibrary);
                        if (localLibrary == null)
                        {
                            throw new IllegalArgumentException("Unable to find genome for remote id: " + remoteLibrary);
                        }
                    }

                    Readset rs = SequenceAnalysisService.get().getReadset(localReadset, getJob().getUser());
                    Container targetWorkbook = ContainerManager.getForId(rs.getContainer());

                    SimpleFilter filter = new SimpleFilter(FieldKey.fromString("readset"), rs.getRowId());
                    filter.addCondition(FieldKey.fromString("runid/JobId/Description"), rd.getValue("runid/JobId/Description"));
                    filter.addCondition(FieldKey.fromString("container"), targetWorkbook.getId(), CompareType.EQUAL);

                    TableSelector tsAnalyses = new TableSelector(analysisTable, PageFlowUtil.set("rowid", "alignmentfile"), filter, null);
                    if (tsAnalyses.exists())
                    {
                        Results results = tsAnalyses.getResults();
                        try
                        {
                            analysisMap.put(remoteId, results.getInt("rowid"));
                            analysisToFileMap.put(results.getInt("rowid"), results.getInt("alignmentfile"));
                        }
                        catch (SQLException e)
                        {
                            throw new RuntimeException(e);
                        }
                    }
                    else
                    {
                        Map<String, Object> toCreate = new CaseInsensitiveHashMap<>();

                        toCreate.put("readset", rs.getRowId());
                        toCreate.put("synopsis", rd.getValue("synopsis"));
                        toCreate.put("centerName", rd.getValue("centerName"));
                        toCreate.put("type", rd.getValue("type"));
                        toCreate.put("description", rd.getValue("description"));
                        toCreate.put("sra_accession", rd.getValue("sra_accession"));
                        Container workbook = workbookMap.get(rd.getValue("workbook/workbookId"));
                        toCreate.put("container", workbook.getId());
                        if (localLibrary != null)
                        {
                            toCreate.put("library_id", localLibrary);
                        }

                        try
                        {
                            if (rd.getValue("runid/JobId") == null)
                            {
                                getJob().getLogger().info("skipping analysis without runid: " + remoteId);
                                return;
                            }

                            int remoteJobId = Integer.parseInt(String.valueOf(rd.getValue("runid/JobId")));
                            int jobId = getOrCreateJob(remoteJobId, targetWorkbook);
                            PipelineStatusFile sf = PipelineService.get().getStatusFile(jobId);

                            String localJobRoot = getParent(sf.getFilePath());
                            String remoteJobRoot = getParent(URI.create(String.valueOf(rd.getValue("runid/JobId/FilePath")).replaceAll(" ", "%20")).getPath());

                            URI newFileAlignment = translateURI(String.valueOf(rd.getValue("alignmentfile/DatafileUrl")), remoteJobRoot, localJobRoot);
                            toCreate.put("alignmentfile", getOrCreateExpData(newFileAlignment, targetWorkbook, String.valueOf(rd.getValue("alignmentfile/Name"))));

                            if (rd.getValue("reference_library") != null)
                            {
                                URI newFile2 = translateURI(String.valueOf(rd.getValue("reference_library/DatafileUrl")), remoteJobRoot, localJobRoot);
                                toCreate.put("reference_library", getOrCreateExpData(newFile2, targetWorkbook, String.valueOf(rd.getValue("reference_library/Name"))));
                            }

                            //Create run:
                            if (rd.getValue("runid") != null && rd.getValue("runid/JobId") != null)
                            {
                                int runId = createExpRun(Integer.parseInt(String.valueOf(rd.getValue("runid"))), targetWorkbook, String.valueOf(rd.getValue("runid/Name")), jobId);
                                toCreate.put("runid", runId);
                            }
                            else
                            {
                                getJob().getLogger().error("analysis missing runid: " + remoteId);
                            }

                            BatchValidationException bve = new BatchValidationException();
                            List<Map<String, Object>> created = analysisTable.getUpdateService().insertRows(getJob().getUser(), getPipelineJob().targetContainer, Arrays.asList(toCreate), bve, null, null);
                            if (bve.hasErrors())
                            {
                                throw new RuntimeException(bve);
                            }

                            analysisMap.put(remoteId, Integer.parseInt(String.valueOf(created.get(0).get("rowid"))));
                            analysisToJobPath.put(Integer.parseInt(String.valueOf(created.get(0).get("rowid"))), remoteJobRoot);
                        }
                        catch (Exception e)
                        {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }
            catch (Exception e)
            {
                getJob().getLogger().error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }

        private void createReaddata()
        {
            getJob().getLogger().info("Creating read data");
            try
            {
                for (Integer workbookId : workbookMap.keySet())
                {
                    final TableInfo readdataTable = QueryService.get().getUserSchema(getJob().getUser(), workbookMap.get(workbookId), "sequenceanalysis").getTable("readdata");

                    SelectRowsCommand sr = new SelectRowsCommand("sequenceanalysis", "readdata");
                    sr.setColumns(Arrays.asList("rowid", "readset", "platformUnit", "centerName", "date", "fileid1", "fileid1/DataFileUrl", "fileid1/Name", "fileid2", "fileid2/DataFileUrl", "fileid2/Name", "description", "sra_accession", "runid", "runid/jobid", "runid/Name", "readset/workbook/workbookId", "runid/JobId", "runid/Name", "runid/JobId/FilePath", "runid/JobId/Description"));

                    SelectRowsResponse srr = sr.execute(getConnection(), getPipelineJob().remoteServerFolder + workbookId + "/");

                    long existing = new TableSelector(readdataTable).getRowCount();
                    if (srr.getRowCount().longValue() == existing)
                    {
                        getJob().getLogger().info("Readdata count identical, skipping: " + workbookId);
                        continue;
                    }
                    else if (srr.getRowCount().intValue() == 0)
                    {
                        getJob().getLogger().info("No readdata records, skipping: " + workbookId);
                        continue;
                    }

                    srr.getRowset().forEach(rd -> {
                        int remoteId = Integer.parseInt(String.valueOf(rd.getValue("rowid")));
                        int remoteReadset = Integer.parseInt(String.valueOf(rd.getValue("readset")));
                        Integer localReadset = readsetMap.get(remoteReadset);
                        if (localReadset == null)
                        {
                            throw new IllegalArgumentException("Unable to find readset for remote id: " + remoteReadset);
                        }

                        Readset rs = SequenceAnalysisService.get().getReadset(localReadset, getJob().getUser());
                        Container targetWorkbook = ContainerManager.getForId(rs.getContainer());

                        SimpleFilter rdFilter = new SimpleFilter(FieldKey.fromString("readset"), rs.getRowId());
                        rdFilter.addCondition(FieldKey.fromString("runid/JobId/Description"), rd.getValue("runid/JobId/Description"));
                        rdFilter.addCondition(FieldKey.fromString("fileid1/Name"), rd.getValue("fileid1/Name"));
                        rdFilter.addCondition(FieldKey.fromString("container"), targetWorkbook.getId(), CompareType.EQUAL);

                        if (rd.getValue("platformUnit") != null)
                        {
                            rdFilter.addCondition(FieldKey.fromString("platformUnit"), rd.getValue("platformUnit"));
                        }

                        TableSelector tsReaddata = new TableSelector(readdataTable, PageFlowUtil.set("rowid"), rdFilter, null);
                        if (tsReaddata.exists())
                        {
                            readdataMap.put(remoteId, tsReaddata.getObject(Integer.class));
                        }
                        else
                        {
                            if (rd.getValue("fileid1/DataFileUrl") == null)
                            {
                                getJob().getLogger().warn("readddata missing files, skipping: " + remoteId);
                                return;
                            }

                            Map<String, Object> toCreate = new CaseInsensitiveHashMap<>();
                            toCreate.put("readset", rs.getRowId());
                            Container workbook = workbookMap.get(rd.getValue("readset/workbook/workbookId"));
                            toCreate.put("container", workbook.getId());
                            toCreate.put("platformUnit", rd.getValue("platformUnit"));
                            toCreate.put("centerName", rd.getValue("centerName"));
                            toCreate.put("date", rd.getValue("date"));
                            toCreate.put("description", rd.getValue("description"));
                            toCreate.put("sra_accession", rd.getValue("sra_accession"));
                            try
                            {
                                if (rd.getValue("runid/JobId") != null)
                                {
                                    int remoteJobId = Integer.parseInt(String.valueOf(rd.getValue("runid/JobId")));
                                    int jobId = getOrCreateJob(remoteJobId, targetWorkbook);
                                    PipelineStatusFile sf = PipelineService.get().getStatusFile(jobId);

                                    String localJobRoot = getParent(sf.getFilePath());
                                    String remoteJobRoot = getParent(URI.create(String.valueOf(rd.getValue("runid/JobId/FilePath")).replaceAll(" ", "%20")).getPath());

                                    if (rd.getValue("fileid1/DataFileUrl") != null)
                                    {
                                        URI newFile1 = translateURI(String.valueOf(rd.getValue("fileid1/DataFileUrl")), remoteJobRoot, localJobRoot);
                                        toCreate.put("fileid1", getOrCreateExpData(newFile1, targetWorkbook, String.valueOf(rd.getValue("fileid1/Name"))));
                                    }

                                    if (rd.getValue("fileid2/DataFileUrl") != null)
                                    {
                                        URI newFile2 = translateURI(String.valueOf(rd.getValue("fileid2/DatafileUrl")), remoteJobRoot, localJobRoot);
                                        toCreate.put("fileid2", getOrCreateExpData(newFile2, targetWorkbook, String.valueOf(rd.getValue("fileid2/Name"))));
                                    }
                                }
                                else
                                {
                                    if (rd.getValue("fileid1/DataFileUrl") != null)
                                    {
                                        getJob().getLogger().error("readddata missing jobid: " + remoteId);
                                    }
                                }

                                //Create run:
                                if (rd.getValue("runid") != null && rd.getValue("runid/JobId") != null)
                                {
                                    int remoteJobId = Integer.parseInt(String.valueOf(rd.getValue("runid/JobId")));
                                    int jobId = getOrCreateJob(remoteJobId, targetWorkbook);
                                    int runId = createExpRun(Integer.parseInt(String.valueOf(rd.getValue("runid"))), targetWorkbook, String.valueOf(rd.getValue("runid/Name")), jobId);
                                    toCreate.put("runid", runId);
                                }
                                else
                                {
                                    if (rd.getValue("fileid1/DataFileUrl") != null)
                                    {
                                        getJob().getLogger().error("readddata missing runid: " + remoteId);
                                    }
                                }

                                BatchValidationException bve = new BatchValidationException();
                                List<Map<String, Object>> created = readdataTable.getUpdateService().insertRows(getJob().getUser(), getPipelineJob().targetContainer, Arrays.asList(toCreate), bve, null, null);
                                if (bve.hasErrors())
                                {
                                    throw new RuntimeException(bve);
                                }

                                readdataMap.put(remoteId, Integer.parseInt(String.valueOf(created.get(0).get("rowid"))));
                            }
                            catch (Exception e)
                            {
                                throw new RuntimeException(e);
                            }
                        }
                    });
                }
            }
            catch(Exception e)
            {
                getJob().getLogger().error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }

        private int getOrCreateExpData(URI file, Container workbook, String fileName)
        {
            ExpData ret = ExperimentService.get().getExpDataByURL(new File(file), workbook);
            if (ret == null)
            {
                ret = ExperimentService.get().createData(workbook, new DataType("Data"), fileName);
                ret.setDataFileURI(file);
                ret.save(getJob().getUser());
            }

            return ret.getRowId();
        }

        private void createReadsets()
        {
            getJob().getLogger().info("Creating readsets");
            try
            {
                final UserSchema us = QueryService.get().getUserSchema(getJob().getUser(), getPipelineJob().targetContainer, "sequenceanalysis");
                final TableInfo readsetTable = us.getTable("sequence_readsets");

                SelectRowsCommand sr = new SelectRowsCommand("sequenceanalysis", "sequence_readsets");
                sr.setColumns(Arrays.asList("rowid", "name", "platform", "application", "librarytype", "chemistry", "comments", "status", "subjectid", "subjectdate", "sampletype", "sampleid", "barcode5", "barcode3", "runid", "runid/jobid", "runid/Name", "workbook/workbookId", "runid/JobId", "runid/Name", "runid/JobId/FilePath"));

                SelectRowsResponse srr = sr.execute(getConnection(), getPipelineJob().remoteServerFolder);

                srr.getRowset().forEach(rs -> {
                    int remoteId = Integer.parseInt(String.valueOf(rs.getValue("rowid")));
                    int sourceWorkbook = Integer.parseInt(String.valueOf(rs.getValue("workbook/workbookId")));
                    Container targetWorkbook = workbookMap.get(sourceWorkbook);
                    if (targetWorkbook == null)
                    {
                        throw new IllegalArgumentException("Unable to find local workbook for source: " + sourceWorkbook);
                    }

                    SimpleFilter rsFilter = new SimpleFilter(FieldKey.fromString("name"), rs.getValue("name"));
                    rsFilter.addCondition(FieldKey.fromString("container"), targetWorkbook.getId(), CompareType.EQUAL);
                    if (rs.getValue("subjectid") != null)
                    {
                        rsFilter.addCondition(FieldKey.fromString("subjectid"), rs.getValue("subjectid"), CompareType.EQUAL);
                    }

                    TableSelector tsReadset = new TableSelector(readsetTable, PageFlowUtil.set("rowid"), rsFilter, null);
                    if (tsReadset.exists())
                    {
                        readsetMap.put(remoteId, tsReadset.getObject(Integer.class));
                    }
                    else
                    {
                        Map<String, Object> toCreate = new CaseInsensitiveHashMap<>();
                        toCreate.put("name", rs.getValue("name"));
                        toCreate.put("platform", rs.getValue("platform"));
                        toCreate.put("application", rs.getValue("application"));
                        toCreate.put("barcode5", rs.getValue("barcode5"));
                        toCreate.put("barcode3", rs.getValue("barcode3"));
                        toCreate.put("subjectid", rs.getValue("subjectid"));

                        toCreate.put("sampleid", rs.getValue("sampleid"));
                        toCreate.put("sampledate", rs.getValue("sampledate"));
                        toCreate.put("librarytype", rs.getValue("librarytype"));
                        toCreate.put("sampletype", rs.getValue("sampletype"));
                        toCreate.put("chemistry", rs.getValue("chemistry"));
                        toCreate.put("comments", rs.getValue("comments"));
                        toCreate.put("status", rs.getValue("status"));

                        toCreate.put("container", targetWorkbook.getId());

                        try
                        {
                            //Create run:
                            if (rs.getValue("runid") != null && rs.getValue("runid/JobId") != null)
                            {
                                int remoteJobId = Integer.parseInt(String.valueOf(rs.getValue("runid/JobId")));
                                int jobId = getOrCreateJob(remoteJobId, targetWorkbook);
                                int runid = createExpRun(Integer.parseInt(String.valueOf(rs.getValue("runid"))), targetWorkbook, String.valueOf(rs.getValue("runid/Name")), jobId);
                                toCreate.put("runid", runid);
                            }
                            else
                            {
                                getJob().getLogger().error("readset missing run id: " + remoteId);
                            }

                            BatchValidationException bve = new BatchValidationException();
                            List<Map<String, Object>> created = readsetTable.getUpdateService().insertRows(getJob().getUser(), getPipelineJob().targetContainer, Arrays.asList(toCreate), bve, null, null);
                            if (bve.hasErrors())
                            {
                                throw new RuntimeException(bve);
                            }

                            readsetMap.put(remoteId, Integer.parseInt(String.valueOf(created.get(0).get("rowid"))));
                        }
                        catch (Exception e)
                        {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }
            catch (Exception e)
            {
                getJob().getLogger().error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }

        private int getOrCreateJob(int remoteJobId, Container targetWorkbook)
        {
            if (jobIdMap.containsKey(remoteJobId))
            {
                return jobIdMap.get(remoteJobId);
            }

            TableInfo ti = DbSchema.get("pipeline", DbSchemaType.Module).getTable("StatusFiles");

            try
            {
                SelectRowsCommand sr = new SelectRowsCommand("pipeline", "job");
                sr.addFilter(new Filter("rowid", remoteJobId, Filter.Operator.EQUAL));
                sr.setColumns(Arrays.asList("RowId", "Info", "FilePath", "Email", "Description", "DataUrl", "Job", "Provider", "HadError", "ActiveTaskId"));

                SelectRowsResponse srr = sr.execute(getConnection(), getPipelineJob().remoteServerFolder);

                File fr = PipelineService.get().getPipelineRootSetting(targetWorkbook).getRootPath();

                AtomicInteger ret = new AtomicInteger();
                srr.getRowset().forEach(pj -> {
                    String filepath = String.valueOf(pj.getValue("FilePath"));
                    if (!filepath.contains("@files"))
                    {
                        //This appears to be an error in PRIMe's data:
                        if (filepath.contains("illuminaImport"))
                        {
                            filepath = filepath.replace("illuminaImport", "@files/illuminaImport");
                        }
                        else if (filepath.contains("sequenceAnalysis"))
                        {
                            filepath = filepath.replace("sequenceAnalysis", "@files/sequenceAnalysis");
                        }
                        else
                        {
                            getJob().getLogger().error("Unexpected filepath: " + pj.getValue("FilePath"));
                        }
                    }

                    File remoteDir = new File(URI.create(filepath.replaceAll(" ", "%20")).getPath());
                    File localDir = new File(fr, filepath.split("@files")[1]);

                    //Check for existing row:
                    TableSelector ts = new TableSelector(ti, PageFlowUtil.set("RowId"), new SimpleFilter(FieldKey.fromString("Job"), pj.getValue("Job")), null);
                    if (ts.exists())
                    {
                        ret.set(ts.getObject(Integer.class));
                    }
                    else
                    {
                        ts = new TableSelector(ti, PageFlowUtil.set("RowId"), new SimpleFilter(FieldKey.fromString("FilePath"), localDir.getPath()), null);
                        if (ts.exists())
                        {
                            ret.set(ts.getObject(Integer.class));
                        }
                        else
                        {
                            Map<String, Object> toCreate = new CaseInsensitiveHashMap<>();
                            toCreate.put("Info", pj.getValue("Info"));
                            toCreate.put("FilePath", localDir.getPath());
                            toCreate.put("Email", pj.getValue("Email"));
                            toCreate.put("Description", pj.getValue("Description"));
                            toCreate.put("DataUrl", pj.getValue("DataUrl"));
                            toCreate.put("Job", pj.getValue("Job"));
                            toCreate.put("Provider", pj.getValue("Provider"));
                            toCreate.put("HadError", pj.getValue("HadError"));
                            toCreate.put("ActiveTaskId", pj.getValue("ActiveTaskId"));
                            toCreate.put("Container", targetWorkbook.getId());

                            toCreate = Table.insert(getJob().getUser(), ti, toCreate);

                            ret.set((int) toCreate.get("RowId"));
                        }
                    }

                    if (localDir.exists())
                    {
                        getJob().getLogger().info("Directory exists, will not re-copy: " + localDir.getPath());
                        return;
                    }

                    try
                    {
                        getJob().getLogger().info(remoteDir.getPath());
                        getJob().getLogger().info(localDir.getPath());

                        if (!localDir.getParentFile().exists())
                        {
                            localDir.getParentFile().mkdirs();
                        }

                        if (remoteDir.exists())
                        {
                            if (!localDir.exists())
                            {
                                throw new PipelineJobException("Expected folder to have been copied: " + remoteDir.getPath() + " to " + localDir.getPath());
                            }
                        }
                        else
                        {
                            getJob().getLogger().error("source folder not found: " + remoteDir.getPath());
                        }
                    }
                    catch (Exception e)
                    {
                        throw new RuntimeException(e);
                    }
                });

                jobIdMap.put(remoteJobId, ret.get());

                return ret.get();
            }
            catch (Exception e)
            {
                getJob().getLogger().error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }

        private int createExpRun(int remoteId, Container c, String name, int localJobId) throws Exception
        {
            if (runIdMap.containsKey(remoteId))
            {
                return runIdMap.get(remoteId);
            }
            else
            {
                ExpRun ret = ExperimentService.get().createRunForProvenanceRecording(c, getJob().getUser(), new RecordedActionSet(), name, localJobId);
                runIdMap.put(remoteId, ret.getRowId());

                return ret.getRowId();
            }
        }

        private void createWorkbooks()
        {
            getJob().getLogger().info("Creating workbooks");
            try
            {
                TableInfo containers = QueryService.get().getUserSchema(getJob().getUser(), getPipelineJob().targetContainer, "core").getTable("containers");

                SelectRowsCommand sr = new SelectRowsCommand("core", "workbooks");
                sr.setColumns(Arrays.asList("Name", "Title", "Description"));
                SelectRowsResponse srr = sr.execute(getConnection(), getPipelineJob().remoteServerFolder);

                srr.getRowset().forEach(wb -> {
                    String localTitle = (String) wb.getValue("Title");

                    TableSelector ts = new TableSelector(containers, PageFlowUtil.set("RowId"), new SimpleFilter(FieldKey.fromString("Title"), localTitle), null);
                    if (ts.exists())
                    {
                        Container workbook = ContainerManager.getForRowId(ts.getObject(Integer.class));
                        workbookMap.put(Integer.parseInt(String.valueOf(wb.getValue("Name"))), workbook);
                    }
                    else
                    {
                        PipeRoot pr = PipelineService.get().getPipelineRootSetting(getJob().getContainer());

                        String description = wb.getValue("Description") != null ? String.valueOf(wb.getValue("Description")) + ". " : "";
                        description = description + "Originally PRIMe workbook: " + wb.getValue("Name");

                        Container workbook = ContainerManager.createContainer(getPipelineJob().targetContainer, null, localTitle, description, WorkbookContainerType.NAME, getJob().getUser());
                        workbook.setFolderType(FolderTypeManager.get().getFolderType("Expt Workbook"), getJob().getUser());
                        workbookMap.put(Integer.parseInt(String.valueOf(wb.getValue("Name"))), workbook);

                        File sourceDir = new File("/home/groups/miSeqLK/Production/MHC_Typing", wb.getValue("Name") + "/@files");
                        File targetDir = pr.getRootPath();
                        if (sourceDir.exists())
                        {
                            try
                            {
                                FileUtils.copyDirectory(sourceDir, targetDir);
                            }
                            catch (Exception e)
                            {
                                throw new RuntimeException(e);
                            }
                        }
                        else
                        {
                            getJob().getLogger().error("source folder not found: " + sourceDir.getPath());
                        }
                    }
                });
            }
            catch (CommandException | IOException e)
            {
                throw new RuntimeException(e);
            }
        }

        private URI translateURI(String databaseURI, String remoteFolderRoot, String localFolderRoot)
        {
            databaseURI = databaseURI.replace("\\", "/");
            remoteFolderRoot = remoteFolderRoot.replace("\\", "/").split("@files")[0];
            localFolderRoot = localFolderRoot.replace("\\", "/").split("@files")[0];
            if (localFolderRoot.startsWith("C:"))
            {
                localFolderRoot = localFolderRoot.replaceAll("^C:", "");
            }

            databaseURI = databaseURI.replace(remoteFolderRoot, localFolderRoot);

            return URI.create(databaseURI);
        }
    }
}
