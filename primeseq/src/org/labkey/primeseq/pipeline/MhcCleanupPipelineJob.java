package org.labkey.primeseq.pipeline;

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.files.FileUrls;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.CancelledException;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

public class MhcCleanupPipelineJob extends PipelineJob
{
    private boolean _performDeletes;
    private int _minAnalysisId;

    private boolean _dropDisabledResults = true;
    private double _lineageThreshold = 0.25;
    private double _alleleGroupThreshold = 0.1;
    private boolean _dropMultiLineageMHC = true;
    private boolean _combineRedundantGroups = true;

    public static class Provider extends PipelineProvider
    {
        public static final String NAME = "mhcCleanupPipeline";

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
    protected MhcCleanupPipelineJob()
    {
    }

    public MhcCleanupPipelineJob(Container c, User u, ActionURL url, PipeRoot pipeRoot, boolean performDeletes, int minAnalysisId)
    {
        super(Provider.NAME, new ViewBackgroundInfo(c, u, url), pipeRoot);

        File subdir = new File(pipeRoot.getRootPath(), Provider.NAME);
        if (!subdir.exists())
        {
            subdir.mkdirs();
        }

        setLogFile(new File(subdir, FileUtil.makeFileNameWithTimestamp("mhcCleanup", "log")));
        _performDeletes = performDeletes;
        _minAnalysisId = minAnalysisId;
    }

    @Override
    public ActionURL getStatusHref()
    {
        return PageFlowUtil.urlProvider(FileUrls.class).urlBegin(getContainer());
    }

    @Override
    public String getDescription()
    {
        return "Cleanup MHC Data";
    }

    @Override
    public TaskPipeline<?> getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(MhcCleanupPipelineJob.class));
    }

    public boolean isDropDisabledResults()
    {
        return _dropDisabledResults;
    }

    public double getLineageThreshold()
    {
        return _lineageThreshold;
    }

    public double getAlleleGroupThreshold()
    {
        return _alleleGroupThreshold;
    }

    public boolean doPerformDeletes()
    {
        return _performDeletes;
    }

    public int getMinAnalysisId()
    {
        return _minAnalysisId;
    }

    public boolean isCombineRedundantGroups()
    {
        return _combineRedundantGroups;
    }

    public boolean isDropMultiLineageMHC()
    {
        return _dropMultiLineageMHC;
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
                setLocation("webserver-high-priority");
            }

            @Override
            public List<FileType> getInputTypes()
            {
                return Collections.emptyList();
            }

            @Override
            public String getStatusName()
            {
                return TaskStatus.running.toString();
            }

            @Override
            public List<String> getProtocolActionNames()
            {
                return List.of("Cleanup MHC Data");
            }

            @Override
            public PipelineJob.Task<?> createTask(PipelineJob job)
            {
                return new Task(this, job);
            }

            @Override
            public boolean isJobComplete(PipelineJob job)
            {
                return false;
            }
        }

        private MhcCleanupPipelineJob getPipelineJob()
        {
            return (MhcCleanupPipelineJob) getJob();
        }

        private Map<String, Integer> alignmentSummaryDeleted = new HashMap<>();
        private Map<String, Integer> alignmentSummaryJunctionDeleted = new HashMap<>();
        @Override
        public RecordedActionSet run() throws PipelineJobException
        {
            UserSchema sequenceAnalysisSchema = QueryService.get().getUserSchema(getJob().getUser(), getJob().getContainer(), "sequenceanalysis");
            TableInfo alignmentSummary = sequenceAnalysisSchema.getTable("alignment_summary");
            TableInfo alignmentSummaryJunction = sequenceAnalysisSchema.getTable("alignment_summary_junction");

            getJob().getLogger().info("Starting rows in alignment_summary: " + new TableSelector(alignmentSummary).getRowCount());
            getJob().getLogger().info("Starting rows in alignment_summary_junction: " + new TableSelector(alignmentSummaryJunction).getRowCount());
            getJob().getLogger().info(getPipelineJob().doPerformDeletes() ? "**This is a production run, records will be deleted" : "**This is a test run, records will not be deleted");

            // Iterate each analysis:
            AtomicInteger ai = new AtomicInteger(0);
            SimpleFilter filter = new SimpleFilter();
            if (getPipelineJob().getMinAnalysisId() > 0)
            {
                filter.addCondition(FieldKey.fromString("rowid"), getPipelineJob().getMinAnalysisId(), CompareType.GT);
            }

            TableInfo sequenceAnalyses = sequenceAnalysisSchema.getTable("sequence_analyses");
            final long totalAnalyses = new TableSelector(sequenceAnalyses).getRowCount();

            new TableSelector(sequenceAnalyses, PageFlowUtil.set("rowId"), filter, new Sort("-rowid")).forEachResults(rs -> {
                ai.getAndIncrement();
                if (ai.get() % 50 == 0)
                {
                    getJob().getLogger().info("Processed " + ai.get() + " analysis records of " + totalAnalyses);
                }

                if (getJob().isCancelled())
                {
                    throw new CancelledException();
                }

                int rowId = rs.getInt(FieldKey.fromString("rowId"));
                getJob().setStatus(TaskStatus.running, "Processing: " + ai.get() + " of " + totalAnalyses);
                processAnalysis(rowId);
            });

            getJob().getLogger().info("Deleted rows in alignment_summary:");
            alignmentSummaryDeleted.keySet().forEach(x -> getJob().getLogger().info(x + ": " + alignmentSummaryDeleted.get(x)));

            getJob().getLogger().info("Deleted rows in alignment_summary_junction: ");
            alignmentSummaryJunctionDeleted.keySet().forEach(x -> getJob().getLogger().info(x + ": " + alignmentSummaryJunctionDeleted.get(x)));

            getJob().getLogger().info("Ending rows in alignment_summary: " + new TableSelector(alignmentSummary).getRowCount());
            getJob().getLogger().info("Ending rows in alignment_summary_junction: " + new TableSelector(alignmentSummaryJunction).getRowCount());

            return new RecordedActionSet();
        }

        private void processAnalysis(int analysisId)
        {
            getJob().getLogger().info("Processing: " + analysisId);
            try (DbScope.Transaction transaction = DbScope.getLabKeyScope().ensureTransaction())
            {
                TableInfo alignmentSummary = DbSchema.get("sequenceanalysis", DbSchemaType.Module).getTable("alignment_summary");
                TableInfo alignmentSummaryJunction = DbSchema.get("sequenceanalysis", DbSchemaType.Module).getTable("alignment_summary_junction");

                // snapshot data:
                final Map<String, Double> existingData = new HashMap<>();
                SimpleFilter dataFilter = new SimpleFilter(FieldKey.fromString("analysis_id"), analysisId, CompareType.EQUAL);
                dataFilter.addCondition(FieldKey.fromString("percent_from_locus"), getPipelineJob().getLineageThreshold(), CompareType.GT);
                new TableSelector(QueryService.get().getUserSchema(getJob().getUser(), getJob().getContainer(), "sequenceanalysis").getTable("alignment_summary_by_lineage"), PageFlowUtil.set("lineages", "percent_from_locus"), dataFilter, null).forEachResults(rs -> {
                    existingData.put(rs.getString(FieldKey.fromString("lineages")), rs.getDouble(FieldKey.fromString("percent_from_locus")));
                });

                // Delete low-freq lineages:
                if (getPipelineJob().getLineageThreshold() > 0)
                {
                    SimpleFilter filter = new SimpleFilter(FieldKey.fromString("analysis_id"), analysisId, CompareType.EQUAL);
                    filter.addCondition(FieldKey.fromString("percent_from_locus"), getPipelineJob().getLineageThreshold(), CompareType.LT);

                    List<String> lowFreqRowIdList = new TableSelector(QueryService.get().getUserSchema(getJob().getUser(), getJob().getContainer(), "sequenceanalysis").getTable("alignment_summary_by_lineage"), PageFlowUtil.set("rowids"), filter, null).getArrayList(String.class);
                    if (!lowFreqRowIdList.isEmpty())
                    {
                        getJob().getLogger().info("Analysis: " + analysisId + ", low freq lineages: " + lowFreqRowIdList.size());
                        List<Integer> alignmentIdsToDelete = lowFreqRowIdList.stream().map(x -> Arrays.asList(x.split(","))).flatMap(List::stream).map(Integer::parseInt).toList();
                        if (!alignmentIdsToDelete.isEmpty())
                        {
                            deleteAlignmentSummaryAndJunction(alignmentIdsToDelete, analysisId, alignmentSummary, alignmentSummaryJunction, "below lineage threshold");
                        }
                    }
                }

                // Delete low-freq allele groups:
                if (getPipelineJob().getAlleleGroupThreshold() > 0)
                {
                    SimpleFilter filter = new SimpleFilter(FieldKey.fromString("analysis_id"), analysisId, CompareType.EQUAL);
                    filter.addCondition(FieldKey.fromString("percent_from_locus"), getPipelineJob().getAlleleGroupThreshold(), CompareType.LT);

                    List<String> lowFreqRowIdList = new TableSelector(QueryService.get().getUserSchema(getJob().getUser(), getJob().getContainer(), "sequenceanalysis").getTable("alignment_summary_grouped"), PageFlowUtil.set("rowids"), filter, null).getArrayList(String.class);
                    if (!lowFreqRowIdList.isEmpty())
                    {
                        getJob().getLogger().info("Analysis: " + analysisId + ", low freq allele groups: " + lowFreqRowIdList.size());
                        List<Integer> alignmentIdsToDelete = lowFreqRowIdList.stream().map(x -> Arrays.asList(x.split(","))).flatMap(List::stream).map(Integer::parseInt).toList();
                        if (!alignmentIdsToDelete.isEmpty())
                        {
                            deleteAlignmentSummaryAndJunction(alignmentIdsToDelete, analysisId, alignmentSummary, alignmentSummaryJunction, "below allele-group threshold");
                        }
                    }
                }

                if (getPipelineJob().isDropDisabledResults())
                {
                    SimpleFilter filter = new SimpleFilter(FieldKey.fromString("analysis_id"), analysisId, CompareType.EQUAL);
                    filter.addCondition(FieldKey.fromString("status"), false, CompareType.EQUAL);

                    List<Integer> junctionRecordsToDelete = new TableSelector(alignmentSummaryJunction, PageFlowUtil.set("rowid"), filter, null).getArrayList(Integer.class);
                    if (!junctionRecordsToDelete.isEmpty())
                    {
                        getJob().getLogger().info("Deleting " + junctionRecordsToDelete.size() + " disabled records from alignment_summary_junction");
                        alignmentSummaryJunctionDeleted.put("disabled records", alignmentSummaryJunctionDeleted.getOrDefault("disabled records", 0) + junctionRecordsToDelete.size());
                        junctionRecordsToDelete.forEach(rowId -> {
                            Table.delete(alignmentSummaryJunction, rowId);
                        });
                    }
                }

                if (getPipelineJob().isDropMultiLineageMHC())
                {
                    SimpleFilter filter = new SimpleFilter(FieldKey.fromString("analysis_id"), analysisId, CompareType.EQUAL);
                    filter.addCondition(FieldKey.fromString("totalLineages"), 1, CompareType.GT);
                    filter.addCondition(FieldKey.fromString("loci"), "MHC", CompareType.CONTAINS);

                    List<String> rowIdList = new TableSelector(QueryService.get().getUserSchema(getJob().getUser(), getJob().getContainer(), "sequenceanalysis").getTable("alignment_summary_grouped"), PageFlowUtil.set("rowids"), filter, null).getArrayList(String.class);
                    if (!rowIdList.isEmpty())
                    {
                        getJob().getLogger().info("Analysis: " + analysisId + ", multi-lineage records: " + rowIdList.size());
                        List<Integer> alignmentIdsToDelete = rowIdList.stream().map(x -> Arrays.asList(x.split(","))).flatMap(List::stream).map(Integer::parseInt).toList();
                        if (!alignmentIdsToDelete.isEmpty())
                        {
                            deleteAlignmentSummaryAndJunction(alignmentIdsToDelete, analysisId, alignmentSummary, alignmentSummaryJunction, "multi-lineage MHC");
                        }
                    }
                }

                // Redundant groups:
                if (getPipelineJob().isCombineRedundantGroups())
                {
                    SimpleFilter nAlignmentFilter = new SimpleFilter(FieldKey.fromString("analysis_id"), analysisId, CompareType.EQUAL);
                    nAlignmentFilter.addCondition(FieldKey.fromString("nAlignments"), 1, CompareType.GT);
                    List<String> redundantAlignmentSets = new TableSelector(QueryService.get().getUserSchema(getJob().getUser(), getJob().getContainer(), "sequenceanalysis").getTable("alignment_summary_grouped"), PageFlowUtil.set("rowids"), nAlignmentFilter, null).getArrayList(String.class);
                    if (!redundantAlignmentSets.isEmpty())
                    {
                        getJob().getLogger().info("Analysis: " + analysisId + ", redundant alignment sets: " + redundantAlignmentSets.size());
                        redundantAlignmentSets.forEach(x -> {
                            List<Integer> alignmentIds = Arrays.stream(x.split(",")).map(Integer::parseInt).toList();
                            if (!alignmentIds.isEmpty())
                            {
                                final AtomicInteger rowIdToAppend = new AtomicInteger(-1);
                                final AtomicInteger originalTotal = new AtomicInteger(0);
                                final AtomicInteger totalToAppend = new AtomicInteger(0);
                                final List<Integer> rowIdsToDelete = new ArrayList<>();
                                new TableSelector(alignmentSummary, PageFlowUtil.set("rowid", "total"), new SimpleFilter(FieldKey.fromString("rowid"), alignmentIds, CompareType.IN), new Sort("-total")).forEachResults(rs -> {
                                    if (rowIdToAppend.get() == -1)
                                    {
                                        rowIdToAppend.set(rs.getInt(FieldKey.fromString("rowid")));
                                        originalTotal.set(rs.getInt(FieldKey.fromString("total")));
                                    }
                                    else
                                    {
                                        rowIdsToDelete.add(rs.getInt(FieldKey.fromString("rowid")));
                                        totalToAppend.getAndAdd(rs.getInt(FieldKey.fromString("total")));
                                    }
                                });

                                deleteAlignmentSummaryAndJunction(rowIdsToDelete, analysisId, alignmentSummary, alignmentSummaryJunction, "redundant allele sets");
                                getJob().getLogger().info("increasing total by: " + totalToAppend.get());
                                Map<String, Object> toUpdate = new CaseInsensitiveHashMap<>();
                                toUpdate.put("rowid", rowIdToAppend.get());
                                toUpdate.put("total", originalTotal.get() + totalToAppend.get());
                                Table.update(getJob().getUser(), alignmentSummary, toUpdate, rowIdToAppend.get());
                            }
                        });
                    }
                }

                // Find alignment_summary records without any remaining results:
                List<Integer> alignmentIdsToDelete = new SqlSelector(alignmentSummary.getSchema().getScope(),
                        new SQLFragment("SELECT x.rowId FROM sequenceanalysis.alignment_summary x ")
                                .append(new SQLFragment("WHERE x.analysis_id = ? ", analysisId))
                                .append(new SQLFragment(" AND x.valid_pairs IS NOT NULL"))
                                .append(new SQLFragment(" AND (SELECT count(*) FROM sequenceanalysis.alignment_summary_junction j WHERE j.analysis_id = ? AND j.alignment_id = x.rowid) = 0", analysisId))
                ).getArrayList(Integer.class);

                if (!alignmentIdsToDelete.isEmpty())
                {
                    getJob().getLogger().info("Deleting " + alignmentIdsToDelete.size() + " alignment_summary records lacking alignment_summary_junction");
                    alignmentSummaryDeleted.put("lacking alignment_summary_junction records", alignmentSummaryDeleted.getOrDefault("lacking alignment_summary_junction records", 0) + alignmentIdsToDelete.size());
                    alignmentIdsToDelete.forEach(rowId -> {
                        Table.delete(alignmentSummary, rowId);
                    });
                }

                // verify ending data:
                final Map<String, Double> endingData = new HashMap<>();
                new TableSelector(QueryService.get().getUserSchema(getJob().getUser(), getJob().getContainer(), "sequenceanalysis").getTable("alignment_summary_by_lineage"), PageFlowUtil.set("lineages", "percent_from_locus"), dataFilter, null).forEachResults(rs -> {
                    endingData.put(rs.getString(FieldKey.fromString("lineages")), rs.getDouble(FieldKey.fromString("percent_from_locus")));
                });

                Set<String> allLineages = new TreeSet<>(existingData.keySet());
                allLineages.addAll(endingData.keySet());
                allLineages.forEach(l -> {
                    if (!existingData.containsKey(l))
                    {
                        getJob().getLogger().error("New lineage >0.25 for analysis: " + analysisId + ", " + l + ", value: " + endingData.get(l));
                    }
                    else if (!endingData.containsKey(l))
                    {
                        if (!l.contains("\n"))
                        {
                            getJob().getLogger().error("Missing lineage >0.25 for analysis: " + analysisId + ", " + l + ", value: " + existingData.get(l));
                        }
                    }
                    else
                    {
                        double pctDiff = Math.abs(existingData.get(l) - endingData.get(l)) / existingData.get(l);
                        if (pctDiff > 0.3)
                        {
                            getJob().getLogger().error("Significant change in freq for lineage: " + l + ", for analysis: " + analysisId + ", change: " + existingData.get(l) + " -> " + endingData.get(l) + ", pct diff: " + pctDiff);
                        }
                    }
                });

                if (getPipelineJob().doPerformDeletes())
                {
                    getJob().getLogger().info("committing changes");
                    transaction.commit();
                }
            }
        }

        private void deleteAlignmentSummaryAndJunction(List<Integer> alignmentIdsToDelete, int analysisId, TableInfo alignmentSummary, TableInfo alignmentSummaryJunction, String reason)
        {
            getJob().getLogger().info("Deleting " + alignmentIdsToDelete.size() + " alignment_summary records. reason: " + reason);
            alignmentSummaryDeleted.put(reason, alignmentSummaryDeleted.getOrDefault(reason, 0) + alignmentIdsToDelete.size());
            alignmentIdsToDelete.forEach(rowId -> {
                Table.delete(alignmentSummary, rowId);
            });

            // also junction records:
            SimpleFilter alignmentIdFilter = new SimpleFilter(FieldKey.fromString("analysis_id"), analysisId, CompareType.EQUAL);
            alignmentIdFilter.addCondition(FieldKey.fromString("alignment_id"), alignmentIdsToDelete, CompareType.IN);
            List<Integer> junctionRecordsToDelete = new TableSelector(alignmentSummaryJunction, PageFlowUtil.set("rowid"), alignmentIdFilter, null).getArrayList(Integer.class);
            getJob().getLogger().info("Deleting " + junctionRecordsToDelete.size() + " alignment_summary_junction records");
            if (!junctionRecordsToDelete.isEmpty())
            {
                alignmentSummaryJunctionDeleted.put(reason, alignmentSummaryJunctionDeleted.getOrDefault(reason, 0) + junctionRecordsToDelete.size());
                junctionRecordsToDelete.forEach(rowId -> {
                    Table.delete(alignmentSummaryJunction, rowId);
                });
            }
        }
    }
}
