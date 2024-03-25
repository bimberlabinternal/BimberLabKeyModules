package org.labkey.pmr.etl;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.di.DataIntegrationService;
import org.labkey.api.di.TaskRefTask;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.writer.ContainerUser;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.CommandResponse;
import org.labkey.remoteapi.PostCommand;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TriggerRemoteGeneticsImportStep implements TaskRefTask
{
    protected final Map<String, String> _settings = new CaseInsensitiveHashMap<>();
    protected ContainerUser _containerUser;

    private enum Settings
    {
        remoteSource()
    }

    @Override
    public RecordedActionSet run(@NotNull PipelineJob job) throws PipelineJobException
    {
        // First find the last successful pipeline iteration:
        Module ehr = ModuleLoader.getInstance().getModule("ehr");
        Module geneticsCore = ModuleLoader.getInstance().getModule("GeneticsCore");

        ModuleProperty mp = ehr.getModuleProperties().get("EHRStudyContainer");
        String ehrContainerPath = StringUtils.trimToNull(mp.getEffectiveValue(_containerUser.getContainer()));
        if (ehrContainerPath == null)
        {
            throw new PipelineJobException("EHRStudyContainer has not been set");
        }

        Container ehrContainer = ContainerManager.getForPath(ehrContainerPath);
        if (ehrContainer == null)
        {
            throw new PipelineJobException("Invalid container: " + ehrContainerPath);
        }

        if (!_containerUser.getContainer().equals(ehrContainer))
        {
            throw new PipelineJobException("This ETL can only be run from the EHRStudyContainer");
        }

        ModuleProperty mp2 = geneticsCore.getModuleProperties().get("KinshipDataPath");
        String pipeDirPath = StringUtils.trimToNull(mp2.getEffectiveValue(ehrContainer));
        if (pipeDirPath == null)
        {
            throw new PipelineJobException("Must provide the filepath to import data using the KinshipDataPath module property");
        }

        File targetPipelineDir = new File(pipeDirPath);
        if (!targetPipelineDir.exists())
        {
            targetPipelineDir.mkdirs();
        }

        // Then copy the file to the expected folder:
        PipeRoot pr = PipelineService.get().getPipelineRootSetting(ehrContainer);
        if (pr == null)
        {
            throw new PipelineJobException("Unable to find pipeline root for: " + ehrContainer);
        }

        File sourceDir = new File(pr.getRootPath(), "/kinship/EHR Kinship Calculation");
        if (!sourceDir.exists())
        {
            throw new PipelineJobException("Unable to find source pipeline dir: " + sourceDir.getPath());
        }

        copyReplaceFile(sourceDir, targetPipelineDir, "kinship.txt");
        copyReplaceFile(sourceDir, targetPipelineDir, "inbreeding.txt");

        // Then ping the main server to import this file:
        DataIntegrationService.RemoteConnection rc = DataIntegrationService.get().getRemoteConnection(_settings.get(Settings.remoteSource.name()), _containerUser.getContainer(), job.getLogger());
        if (rc == null)
        {
            throw new PipelineJobException("Unable to find remote connection: " + _settings.get(Settings.remoteSource.name()));
        }

        try
        {
            KinshipCommand command = new KinshipCommand();
            command.execute(rc.connection, rc.remoteContainer);
        }
        catch (CommandException | IOException e)
        {
            throw new PipelineJobException(e);
        }

        return new RecordedActionSet();
    }

    private static class KinshipCommand extends PostCommand<CommandResponse>
    {
        public KinshipCommand()
        {
            super("geneticscore", "importGeneticsData");
        }
    }

    private void copyReplaceFile(File sourceDir, File targetDir, String filename) throws PipelineJobException
    {
        File sourceFile = new File(sourceDir, filename);
        if (!sourceFile.exists())
        {
            throw new PipelineJobException("File does not exist: " + sourceFile.getPath());
        }

        File destFile = new File(targetDir, filename);
        if (destFile.exists())
        {
            destFile.delete();
        }

        try
        {
            FileUtils.copyFile(sourceFile, destFile);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    @Override
    public List<String> getRequiredSettings()
    {
        return Collections.unmodifiableList(Arrays.asList(Settings.remoteSource.name()));
    }

    @Override
    public void setSettings(Map<String, String> settings) throws XmlException
    {
        _settings.putAll(settings);
    }

    @Override
    public void setContainerUser(ContainerUser containerUser)
    {
        _containerUser = containerUser;
    }
}
