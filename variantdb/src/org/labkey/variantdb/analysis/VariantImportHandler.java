package org.labkey.variantdb.analysis;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.util.FileType;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.variantdb.VariantDBModule;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Created by bimber on 8/26/2014.
 */
public class VariantImportHandler implements SequenceOutputHandler
{
    private final FileType _vcfType = new FileType(Arrays.asList("vcf"), "vcf", false, FileType.gzSupportLevel.SUPPORT_GZ);
    private final FileType _bedType = new FileType(Arrays.asList("bed"), "bed", false, FileType.gzSupportLevel.SUPPORT_GZ);

    public VariantImportHandler()
    {

    }

    @Override
    public String getName()
    {
        return "Import Variants";
    }

    @Override
    public String getDescription()
    {
        return null;
    }

    @Override
    public String getButtonJSHandler()
    {
        return "VariantDB.window.VariantImportWindow.buttonHandler";
    }

    @Override
    public ActionURL getButtonSuccessUrl(Container c, User u, List<Integer> outputFileIds)
    {
        return null;
    }

    @Override
    public Module getOwningModule()
    {
        return ModuleLoader.getInstance().getModule(VariantDBModule.class);
    }

    @Override
    public LinkedHashSet<String> getClientDependencies()
    {
        return new LinkedHashSet<>(Arrays.asList("variantdb/window/VariantImportWindow.js"));
    }

    @Override
    public List<String> validateParameters(JSONObject params)
    {
        return null;
    }

    @Override
    public boolean canProcess(SequenceOutputFile f)
    {
        return f.getFile() != null && (_vcfType.isType(f.getFile()) || _bedType.isType(f.getFile()));
    }

    @Override
    public boolean doRunRemote()
    {
        return false;
    }

    @Override
    public boolean doRunLocal()
    {
        return true;
    }

    @Override
    public OutputProcessor getProcessor()
    {
        return new Processor();
    }

    @Override
    public boolean doSplitJobs()
    {
        return false;
    }

    public class Processor implements OutputProcessor
    {
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            throw new UnsupportedOperationException("JBrowse output handle should not be called through this path");
        }

        @Override
        public void processFilesRemote(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }
    }
}
