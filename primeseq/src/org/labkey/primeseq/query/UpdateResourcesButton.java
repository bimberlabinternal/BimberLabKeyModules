package org.labkey.primeseq.query;

import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.primeseq.PrimeseqModule;

import java.util.Arrays;

public class UpdateResourcesButton extends SimpleButtonConfigFactory
{
    public UpdateResourcesButton()
    {
        super(ModuleLoader.getInstance().getModule(PrimeseqModule.class), "Update Cluster Resources", "Primeseq.window.UpdateJobResourcesWindow.buttonHandler(dataRegionName);", Arrays.asList(
                ClientDependency.supplierFromPath("Ext4"),
                ClientDependency.supplierFromPath("laboratory.context"),
                ClientDependency.supplierFromPath("primeseq/window/UpdateJobResourcesWindow.js"),
                ClientDependency.supplierFromPath("SequenceAnalysis/sequenceAnalysis"),
                ClientDependency.supplierFromPath("SequenceAnalysis/panel/AnalysisSectionPanel.js")
        ));
    }

    @Override
    public boolean isAvailable(TableInfo ti)
    {
        return super.isAvailable(ti) || ContainerManager.getRoot().equals(ti.getUserSchema().getContainer());
    }
}