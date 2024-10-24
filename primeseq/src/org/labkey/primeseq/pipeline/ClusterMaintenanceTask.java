package org.labkey.primeseq.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cluster.ClusterService;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.RemoteExecutionEngine;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.UserManager;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Job;
import org.labkey.api.util.JobRunner;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SystemMaintenance;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * This task is designed to run remotely and will delete orphan working directories on a remote pipeline server
 *
 * Created by bimber on 7/14/2017.
 */
public class ClusterMaintenanceTask implements SystemMaintenance.MaintenanceTask
{
    private static final Logger _log = LogManager.getLogger(ClusterMaintenanceTask.class);

    public ClusterMaintenanceTask()
    {

    }

    @Override
    public String getDescription()
    {
        return "Cluster Maintenance";
    }

    @Override
    public String getName()
    {
        return "ClusterMaintenance";
    }


    @Override
    public void run(Logger log)
    {
        TableInfo ti = DbSchema.get("pipeline", DbSchemaType.Module).getTable("StatusFiles");
        TableSelector ts = new TableSelector(ti, PageFlowUtil.set("Job"), new SimpleFilter(FieldKey.fromString("Status"), "COMPLETE", CompareType.NEQ_OR_NULL), null);
        Set<String> jobGuids = new HashSet<>(ts.getArrayList(String.class));

        TableSelector ts2 = new TableSelector(ti, PageFlowUtil.set("EntityId"), new SimpleFilter(FieldKey.fromString("Status"), "COMPLETE", CompareType.NEQ_OR_NULL), null);
        jobGuids.addAll(ts2.getArrayList(String.class));

        JobRunner jr = JobRunner.getDefault();
        for (RemoteExecutionEngine engine : PipelineJobService.get().getRemoteExecutionEngines())
        {
            log.info("Starting maintenance task for: " + engine.getType());

            try
            {
                RemoteWorkTask task = new RemoteWorkTask(jobGuids);
                PipeRoot pr = PipelineService.get().getPipelineRootSetting(ContainerManager.getHomeContainer());
                File subdir = new File(pr.getRootPath(), "clusterMaintenance");
                if (!subdir.exists())
                {
                    subdir.mkdirs();
                }

                File logFile = new File(subdir, "Maintenance-" + engine.getType() + "." + FileUtil.getTimestamp() + ".log");

                jr.execute(new Job()
                {
                    @Override
                    public void run()
                    {
                        try
                        {
                            PipelineJob job = ClusterService.get().createClusterRemotePipelineJob(ContainerManager.getHomeContainer(), UserManager.getGuestUser(), "Maintenance: " + engine.getType(), engine, task, logFile);
                            PipelineService.get().queueJob(job);
                        }
                        catch (PipelineValidationException e)
                        {
                            _log.error(e);
                        }
                    }
                });
            }
            catch (Exception e)
            {
                log.error(e);
            }
        }

        jr.waitForCompletion();
    }

    @Override
    public boolean isEnabledByDefault()
    {
        return false;
    }

    public static class RemoteWorkTask implements ClusterService.ClusterRemoteTask
    {
        private Set<String> _jobGuids;

        //for serialization
        protected RemoteWorkTask()
        {

        }

        public RemoteWorkTask(Set<String> jobGuids)
        {
            _jobGuids = new CaseInsensitiveHashSet(jobGuids);
        }

        @Override
        public void run(Logger log)
        {
            //TODO: inspect WorkDirFactory for base path?
            //WorkDirFactory wdf = PipelineJobService.get().getWorkDirFactory();

            log.info("total active pipeline jobs: " + _jobGuids.size());

            //hacky, but this is only planned to be used by us
            inspectFolder(log, new File("/home/exacloud/gscratch/prime-seq/workDir/"));
            inspectFolder(log, new File("/home/exacloud/gscratch/prime-seq/cachedData/"));
        }

        private void deleteDirectory(File child, Logger log)
        {
            try
            {
                if (SystemUtils.IS_OS_WINDOWS)
                {
                    FileUtils.deleteDirectory(child);
                }
                else
                {
                    new SimpleScriptWrapper(log).execute(Arrays.asList("rm", "-Rf", child.getPath()));
                }
            }
            catch (IOException | PipelineJobException e)
            {
                log.error("Unable to delete folder: " + child.getPath(), e);
            }
        }

        private void inspectFolder(Logger log, File workDirBase)
        {
            log.info("Inspecting folder: " + workDirBase.getPath());

            if (!workDirBase.exists())
            {
                log.error("Unable to find workdir: " + workDirBase.getPath());
                return;
            }

            File[] subdirs = workDirBase.listFiles();
            log.info("total work directories found: " + subdirs.length);
            for (File child : subdirs)
            {
                if (child.isDirectory())
                {
                    if (!_jobGuids.contains(child.getName()))
                    {
                        log.info("inspecting directory: " + child.getName());
                        Collection<Path> modifiedRecently = new HashSet<>();
                        final long minDate = DateUtils.addDays(new Date(), -2).getTime();
                        try (DirectoryStream<Path> ds = Files.newDirectoryStream(child.toPath(),x -> x.toFile().lastModified() >= minDate))
                        {
                            ds.forEach(x -> modifiedRecently.add(x));
                        }
                        catch (IOException e)
                        {
                            _log.error(e);
                            continue;
                        }

                        if (modifiedRecently.isEmpty())
                        {
                            log.info("deleting directory: " + child.getName());
                            deleteDirectory(child, log);
                        }
                        else
                        {
                            log.info("directory has " + modifiedRecently.size() + " files modified in the last 48H, skipping for now: " + child.getName());
                            for (Path f : modifiedRecently)
                            {
                                log.debug(f.toFile().getPath());
                            }
                        }
                    }
                }
            }
        }

    }

    public static class TestCase extends Assert
    {
        @Test
        public void testSerialization() throws Exception
        {
            RemoteWorkTask task = new RemoteWorkTask();
            task._jobGuids = new HashSet<>();
            task._jobGuids.add("1");

            ObjectMapper mapper = PipelineJob.createObjectMapper();

            StringWriter writer = new StringWriter();
            mapper.writeValue(writer, task);
            RemoteWorkTask deserialized = mapper.readValue(new StringReader(writer.toString()), RemoteWorkTask.class);

            assertEquals("Class not serialized properly", 1, deserialized._jobGuids.size());
            assertEquals("Class not serialized properly", "1", deserialized._jobGuids.iterator().next());
        }
    }
}
