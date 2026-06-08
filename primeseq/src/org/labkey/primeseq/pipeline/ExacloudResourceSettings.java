package org.labkey.primeseq.pipeline;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.sequenceanalysis.pipeline.JobResourceSettings;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.primeseq.PrimeseqModule;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by bimber on 9/30/2016.
 */
public class ExacloudResourceSettings implements JobResourceSettings
{
    @Override
    public List<ToolParameterDescriptor> getParams()
    {
        return Arrays.asList(
                ToolParameterDescriptor.create("cpus", "CPUs", "The number of CPUs requested for this job", "ldk-integerfield", null, null),
                ToolParameterDescriptor.create("ram", "RAM (GB)", "The RAM requested for this job", "ldk-integerfield", null, null),
                ToolParameterDescriptor.create("qos", "Queue Options", "Chose the option that best fits your job needs.  The default queue allows up to 36H, long_jobs are up to 10 days, and very_long_jobs are up to 30 days.", "ldk-simplecombo", new JSONObject(){{
                    put("storeValues", "Default;LongJobs;VeryLongJobs");
                    put("multiSelect", false);
                }}, null),
                //ToolParameterDescriptor.create("highIO", "Add HighIO Flag", "If selected, --qos=highio will be added, which limits the number of concurrent jobs that can run.", "checkbox", null, null),
                ToolParameterDescriptor.create("time", "Requested Time (days/hours)", "If provided, this is passed to the --time argument.  This cannot be higher than the limit for your requested partition.  Examples are: '2124', '1-0' (1 day, 0 hours), '10-0' (10 days), and '0-72' (72 hours).  If left blank, this will be automatically assigned.", "textfield", null, null),
                ToolParameterDescriptor.create("javaProcessXmx", "Java Process Xmx (GB)", "The value to be used as -Xmx for the LabKey remote java process.  Unless you have a good reason, do not change this", "ldk-integerfield", null, null),
                ToolParameterDescriptor.create("localDisk", "Local Disk (GB)", "Do not change this unless you are certain.  Each job requests and uses local space (/mnt/scratch) for temp files.  If your job will require more space, consider increasing this.", "ldk-integerfield", new JSONObject(){{
                    put("minValue", 512);
                }}, 1028),
                ToolParameterDescriptor.create("gpus", "GPUs", "The number of GPUs requested for this job. If non-zero, the gpu partition will be used.", "ldk-integerfield", null, null)
        );
    }

    @Override
    public boolean isAvailable(Container c)
    {
        return c.getActiveModules().contains(ModuleLoader.getInstance().getModule(PrimeseqModule.class));
    }

    @Override
    public Collection<String> getDockerVolumes(Container c)
    {
        Set<String> volumes = new HashSet<>();
        volumes.add("/home/groups/prime-seq");
        volumes.add("/home/exacloud/gscratch");
        volumes.add("/mnt/scratch");

        PipeRoot pr = PipelineService.get().findPipelineRoot(c);
        if (pr != null && pr.getRootPath().exists())
        {
            volumes.add(convertHomeGroups(pr.getRootPath()).getPath());
        }

        if (c.isWorkbook())
        {
            PipeRoot pr2 = PipelineService.get().findPipelineRoot(c.getParent());
            if (pr2 != null && pr2.getRootPath().exists())
            {
                volumes.add(convertHomeGroups(pr2.getRootPath()).getPath());
            }
        }

        return volumes;
    }

    private File convertHomeGroups(File input)
    {
        input = input.isDirectory() ? input : input.getParentFile();
        if (input.getPath().startsWith("/home/groups/"))
        {
            String folderName = input.getPath().replaceAll("^/home/groups/", "").split("/")[0];
            return new File("/home/groups/", folderName);
        }

        return input;
    }

    @Override
    public @Nullable File inferDockerVolume(File input)
    {
        input = input.isDirectory() ? input : input.getParentFile();
        if (input.getPath().startsWith("/home/exacloud/gscratch"))
        {
            return new File("/home/exacloud/gscratch");
        }

        return convertHomeGroups(input);
    }
}
