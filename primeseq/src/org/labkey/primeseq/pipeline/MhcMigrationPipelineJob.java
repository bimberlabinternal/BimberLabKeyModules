package org.labkey.primeseq.pipeline;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.WorkbookContainerType;
import org.labkey.api.di.DataIntegrationService;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileUrls;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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

                createLibraries();
                createLibraryMembers();

                createReadsets();
                transaction.commitAndKeepConnection();

                createReaddata();

                createAnalyses();
                createOutputFiles();

                //TODO:
                //samples
                //alignment_summary
                //alignment_summary_junction
                //quality_metrics
                //subjects
                //WaNPRC

                //sequenceanalysis.haplotypes
                //sequenceanalysis.haplotype_types
                //sequenceanalysis.haplotype_sequences

                //Create assay runs, including data and haplotypes

                transaction.commit();
            }

            return new RecordedActionSet();
        }

        private void replaceEntireTable(String schema, String query, List<String> columns, String workbookColName, boolean truncateExisting) throws Exception
        {
            SelectRowsCommand sr = new SelectRowsCommand(schema, query);
            sr.setColumns(columns);
            SelectRowsResponse srr = sr.execute(getConnection(), getPipelineJob().remoteServerFolder);

            List<Map<String, Object>> toInsert = new ArrayList<>();
            srr.getRowset().forEach(r -> {
                Map<String, Object> row = new CaseInsensitiveHashMap<>();
                srr.getColumnModel().forEach(col -> {
                    String colName = (String) col.get("Name");
                    Object val = r.getValue(colName);
                    if ("readset".equals(colName) || "readsetid".equals(colName))
                    {
                        if (!readsetMap.containsKey((int) val))
                        {
                            throw new IllegalStateException("Unable to find readset: " + val);
                        }

                        val = readsetMap.get((int) val);
                    }
                    else if ("library_id".equals(colName))
                    {
                        if (!libraryMap.containsKey((int) val))
                        {
                            throw new IllegalStateException("Unable to find library: " + val);
                        }

                        val = libraryMap.get((int) val);

                    }
                    else if ("ref_nt_id".equals(colName))
                    {
                        if (!sequenceMap.containsKey((int) val))
                        {
                            throw new IllegalStateException("Unable to find sequence: " + val);
                        }

                        val = sequenceMap.get((int) val);
                    }
                    else if ("analysis_id".equals(colName))
                    {
                        if (!analysisMap.containsKey((int) val))
                        {
                            throw new IllegalStateException("Unable to find analysis: " + val);
                        }

                        val = analysisMap.get((int) val);
                    }

                    row.put(colName, val);
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


        }

        //All of these map remote Id to local Id
        private final Map<Integer, Container> workbookMap = new HashMap<>();
        private final Map<Integer, Integer> readsetMap = new HashMap<>();
        private final Map<Integer, Integer> readdataMap = new HashMap<>();
        private final Map<Integer, Integer> analysisMap = new HashMap<>();
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

                srr.getRowset().forEach(rd -> {
                    int remoteId = Integer.parseInt(String.valueOf(rd.getValue("rowid")));
                    int seqLength = Integer.parseInt(String.valueOf(rd.getValue("ref_nt_id/seqLength")));

                    int remoteSeqId = Integer.parseInt(String.valueOf(rd.getValue("ref_nt_id")));
                    String name = String.valueOf(rd.getValue("ref_nt_id/name"));
                    int localSeqId = getOrCreateSequence(remoteSeqId, name, seqLength, refNtTable);

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

                    try
                    {
                        BatchValidationException bve = new BatchValidationException();
                        List<Map<String, Object>> created = ti.getUpdateService().insertRows(getJob().getUser(), getPipelineJob().targetContainer, Arrays.asList(toCreate), bve, null, null);
                        if (bve.hasErrors())
                        {
                            throw new RuntimeException(bve);
                        }
                    }
                    catch (Exception e)
                    {
                        getJob().getLogger().error(e.getMessage(), e);
                        throw new RuntimeException(e);
                    }
                });
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
                            getJob().getLogger().warn("length doesnt match for " + name + ", expected: " + seqLength);
                            return;
                        }

                        localId.set(rs.getInt(FieldKey.fromString("rowid")));
                    });

                    if (localId.get() != -1)
                    {
                        sequenceMap.put(remoteSeqId, localId.get());
                        return localId.get();
                    }
                }

                //TODO: Create sequence?
                //throw new IllegalStateException("Expected sequence to exist: " + name);
                getJob().getLogger().error("Sequence missing: " + name);
                return -1;
            }
        }

        public String getParent(String path)
        {
            final char separatorChar = '/';

            int index = path.lastIndexOf(separatorChar);

            return path.substring(0, index);
        }

        private void createLibraries()
        {
            getJob().getLogger().info("Creating libraries");
            try
            {
                final TableInfo libraryTable = QueryService.get().getUserSchema(getJob().getUser(), getPipelineJob().targetContainer, "sequenceanalysis").getTable("reference_libraries");

                SelectRowsCommand sr = new SelectRowsCommand("sequenceanalysis", "reference_libraries");
                sr.setColumns(Arrays.asList("rowid", "name", "description", "fasta_file", "datedisabled", "assemblyId", "fasta_file/DataFileUrl", "workbook/workbookId"));

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
                            toCreate.put("fasta_file", getOrCreateExpData(localFasta, targetContainer));

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
                                FileUtils.copyDirectory(remoteJobRootFile, localJobRootFile);
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
                sr.setColumns(Arrays.asList("rowid", "name", "description", "dataid", "library_id", "readset", "analysis_id", "category", "sra_accession", "dataid/DataFileUrl", "runid/jobid", "runid/Name", "workbook/workbookId", "runid/JobId", "runid/Name", "runid/JobId/FilePath"));

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

                    int remoteAnalysis = Integer.parseInt(String.valueOf(rd.getValue("analysis_id")));
                    Integer localAnalysis = analysisMap.get(remoteAnalysis);
                    if (localAnalysis == null)
                    {
                        throw new IllegalArgumentException("Unable to find analysis for remote id: " + remoteAnalysis);
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
                            int remoteJobId = Integer.parseInt(String.valueOf(rd.getValue("runid/JobId")));
                            int jobId = getOrCreateJob(remoteJobId, targetWorkbook);
                            PipelineStatusFile sf = PipelineService.get().getStatusFile(jobId);

                            String localJobRoot = getParent(sf.getFilePath());
                            String remoteJobRoot = getParent(URI.create(String.valueOf(rd.getValue("runid/JobId/FilePath")).replaceAll(" ", "_")).getPath());

                            URI newFileAlignment = translateURI(String.valueOf(rd.getValue("dataid/DatafileUrl")), remoteJobRoot, localJobRoot);
                            toCreate.put("dataid", getOrCreateExpData(newFileAlignment, targetWorkbook));

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
                sr.setColumns(Arrays.asList("rowid", "type", "description", "synopsis", "runid", "readset", "alignmentfile", "reference_library", "library_id", "sra_accession", "alignmentfile/DataFileUrl", "alignmentfile/Name", "reference_library", "reference_library/DataFileUrl", "runid/jobid", "runid/Name", "workbook/workbookId", "runid/JobId", "runid/Name", "runid/JobId/FilePath", "runid/JobId/Description"));

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

                    TableSelector tsAnalyses = new TableSelector(analysisTable, PageFlowUtil.set("rowid"), filter, null);
                    if (tsAnalyses.exists())
                    {
                        analysisMap.put(remoteId, tsAnalyses.getObject(Integer.class));
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
                            String remoteJobRoot = getParent(URI.create(String.valueOf(rd.getValue("runid/JobId/FilePath")).replaceAll(" ", "_")).getPath());

                            URI newFileAlignment = translateURI(String.valueOf(rd.getValue("alignmentfile/DatafileUrl")), remoteJobRoot, localJobRoot);
                            toCreate.put("alignmentfile", getOrCreateExpData(newFileAlignment, targetWorkbook));

                            if (rd.getValue("reference_library") != null)
                            {
                                URI newFile2 = translateURI(String.valueOf(rd.getValue("reference_library/DatafileUrl")), remoteJobRoot, localJobRoot);
                                toCreate.put("reference_library", getOrCreateExpData(newFile2, targetWorkbook));
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
                final TableInfo readdataTable = QueryService.get().getUserSchema(getJob().getUser(), getPipelineJob().targetContainer, "sequenceanalysis").getTable("readdata");

                SelectRowsCommand sr = new SelectRowsCommand("sequenceanalysis", "readdata");
                sr.setColumns(Arrays.asList("rowid", "readset", "platformUnit", "centerName", "date", "fileid1", "fileid1/DataFileUrl", "fileid2", "fileid2/DataFileUrl", "fileid1/Name", "description", "sra_accession", "runid", "runid/jobid", "runid/Name", "readset/workbook/workbookId", "runid/JobId", "runid/Name", "runid/JobId/FilePath", "runid/JobId/Description"));

                SelectRowsResponse srr = sr.execute(getConnection(), getPipelineJob().remoteServerFolder);

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
                        Map<String, Object> toCreate = new CaseInsensitiveHashMap<>();
                        toCreate.put("readset", rs.getRowId());
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
                                String remoteJobRoot = getParent(URI.create(String.valueOf(rd.getValue("runid/JobId/FilePath")).replaceAll(" ", "_")).getPath());

                                if (rd.getValue("fileid1/DataFileUrl") != null)
                                {
                                    URI newFile1 = translateURI(String.valueOf(rd.getValue("fileid1/DataFileUrl")), remoteJobRoot, localJobRoot);
                                    toCreate.put("fileid1", getOrCreateExpData(newFile1, targetWorkbook));
                                }

                                if (rd.getValue("fileid2/DataFileUrl") != null)
                                {
                                    URI newFile2 = translateURI(String.valueOf(rd.getValue("fileid2/DatafileUrl")), remoteJobRoot, localJobRoot);
                                    toCreate.put("fileid2", getOrCreateExpData(newFile2, targetWorkbook));
                                }
                            }
                            else
                            {
                                getJob().getLogger().error("readddata missing jobid: " + remoteId);
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
                                getJob().getLogger().error("readddata missing runid: " + remoteId);
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
            catch (Exception e)
            {
                getJob().getLogger().error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }

        private int getOrCreateExpData(URI file, Container workbook)
        {
            ExpData ret = ExperimentService.get().getExpDataByURL(new File(file), workbook);
            if (ret == null)
            {
                ret = ExperimentService.get().createData(workbook, new DataType("Data"));
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

                    File remoteDir = new File(URI.create(filepath.replaceAll(" ", "_")).getPath());
                    File localDir = new File(fr, filepath.split("@files")[1]);

                    //Check for existing row:
                    TableSelector ts = new TableSelector(ti, PageFlowUtil.set("RowId"), new SimpleFilter(FieldKey.fromString("Job"), pj.getValue("Job")), null);
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
                            FileUtils.copyDirectory(remoteDir, localDir);
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
                        String description = String.valueOf(wb.getValue("Description"));
                        if (description != null)
                        {
                            description = description + ". ";
                        }
                        else
                        {
                            description = "";
                        }

                        description = description + "Originally PRIMe workbook: " + wb.getValue("Name");

                        Container workbook = ContainerManager.createContainer(getPipelineJob().targetContainer, null, localTitle, description, WorkbookContainerType.NAME, getJob().getUser());
                        workbookMap.put(Integer.parseInt(String.valueOf(wb.getValue("Name"))), workbook);
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
