package org.labkey.primeseq.pipeline;

import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.primeseq.PrimeseqModule;

import java.util.List;

/**
 * User: bimber
 * Date: 7/16/2014
 * Time: 5:37 PM
 */
public class DeleteJobCheckpointButton extends SimpleButtonConfigFactory
{
    public DeleteJobCheckpointButton()
    {
        super(ModuleLoader.getInstance().getModule(PrimeseqModule.class), "Delete Job Checkpoint(s)", "Primeseq.window.DeleteJobCheckpointWindow.buttonHandler(dataRegionName);", List.of(ClientDependency.supplierFromPath("primeseq/window/DeleteJobCheckpointWindow.js")));
    }
}
