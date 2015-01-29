package org.labkey.variantdb.analysis;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceOutputHandler;
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
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        return new LinkedHashSet<>(Arrays.asList(ClientDependency.fromPath("variantdb/window/VariantImportWindow.js")));
    }

    @Override
    public boolean canProcess(SequenceOutputFile f)
    {
        return f.getFile() != null && (_vcfType.isType(f.getFile()) || _bedType.isType(f.getFile()));
    }

    @Override
    public void processFiles(PipelineJob job, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
    {
        throw new UnsupportedOperationException("JBrowse output handle should not be called through this path");
    }
}
