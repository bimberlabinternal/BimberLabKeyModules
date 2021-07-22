package org.labkey.mcc.etl;

import com.google.common.io.Files;
import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.di.TaskRefTask;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.ContainerUser;
import org.labkey.mcc.MccManager;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ZimsImportTask implements TaskRefTask
{
    protected ContainerUser _containerUser;

    private static final String lastRunTime = "lastRunTime";
    private static final String PROP_CATEGORY = "mcc.ZimsImportTask";

    // Default constructor for serialization
    public ZimsImportTask()
    {
    }

    @Override
    public List<String> getRequiredSettings()
    {
        return null;
    }

    @Override
    public void setSettings(Map<String, String> settings) throws XmlException
    {

    }

    @Override
    public void setContainerUser(ContainerUser containerUser)
    {

    }

    @Override
    public RecordedActionSet run(@NotNull PipelineJob job) throws PipelineJobException
    {
        Date jobStart = new Date();

        File dir = MccManager.get().getZimsImportFolder(job.getContainer());
        if (dir == null)
        {
            job.getLogger().info("ZIMSImportPath module property not set, aborting");
            return null;
        }

        File animalDir = new File(dir, "Animal");
        if (animalDir.exists())
        {
            dir = animalDir;
        }

        if (!dir.exists())
        {
            throw new PipelineJobException("Unable to find folder: " + dir.getPath());
        }

        Date lastRun = getLastRun(job.getContainer());
        List<Map<String, Object>> demographicsToInsert = new ArrayList<>();

        Set<String> skippedFiles = new HashSet<>();
        File[] xmls = dir.listFiles(x -> x.getName().endsWith(".xml"));
        for (File x : xmls)
        {
            if (lastRun != null && x.lastModified() < lastRun.getTime())
            {
                continue;
            }

            job.getLogger().info("Processing: " + x.getName());
            ZimsAnimalRecord r = ZimsAnimalRecord.processAnimal(x, job.getLogger());

            try
            {
                demographicsToInsert.add(r.toDemographicsRecord());
            }
            catch (IllegalStateException e)
            {
                skippedFiles.add(x.getName());
                job.getLogger().error("Error processing: " + x.getName() + ", it will be skipped", e);

                //Touch file so it will be repeated next time:
                try
                {
                    Files.touch(x);
                }
                catch (IOException e2)
                {
                    job.getLogger().error("Error running touch on: " + x.getName(), e2);
                }
            }
        }

        job.getLogger().info("Total records to update: " + demographicsToInsert.size());
        if (!demographicsToInsert.isEmpty())
        {
            TableInfo ti = QueryService.get().getUserSchema(job.getUser(), job.getContainer(), "study").getTable("demographics");
            try
            {
                Map<String, Object> configParams = new HashMap<>();
                configParams.put("dataSource", "etl");

                Set<String> uniqueIds = demographicsToInsert.stream().map(x -> x.get("Id")).map(Object::toString).collect(Collectors.toSet());
                Map<String, Object>[] existing = new TableSelector(ti, PageFlowUtil.set("lsid", "Id"), new SimpleFilter(FieldKey.fromString("Id"), uniqueIds, CompareType.IN), null).getMapArray();
                if (existing.length > 0)
                {
                    List<Map<String, Object>> toDelete = Arrays.stream(existing).collect(Collectors.toList());
                    ti.getUpdateService().deleteRows(job.getUser(), job.getContainer(), toDelete, null, configParams);
                }

                BatchValidationException errors = new BatchValidationException();
                ti.getUpdateService().insertRows(job.getUser(), job.getContainer(), demographicsToInsert, errors, null, configParams);
                if (errors.hasErrors())
                {
                    throw errors;
                }
            }
            catch (InvalidKeyException | DuplicateKeyException | BatchValidationException | QueryUpdateServiceException | SQLException e)
            {
                throw new PipelineJobException(e);
            }
        }

        if (!skippedFiles.isEmpty())
        {
            job.getLogger().info("The following files could not be processed and were skipped:");
            skippedFiles.forEach(x -> job.getLogger().info(x));
        }

        job.setStatus(PipelineJob.TaskStatus.running);
        saveLastRun(job.getContainer(), jobStart);

        return new RecordedActionSet();
    }

    public static void saveLastRun(Container container, Date jobStart)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(container, PROP_CATEGORY, true);
        if (jobStart != null)
        {
            map.put(lastRunTime, String.valueOf(jobStart.getTime()));
        }
        else
        {
            map.remove(lastRunTime);
        }

        map.save();
    }

    private static Date getLastRun(Container container)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(container, PROP_CATEGORY, true);
        return map.containsKey(lastRunTime) ? new Date(Long.parseLong(map.get(lastRunTime))) : null;
    }
}
