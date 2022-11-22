package org.labkey.labpurchasing.buttons;

import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.Module;
import org.labkey.api.view.template.ClientDependency;

public class MarkReceivedButton extends SimpleButtonConfigFactory
{
    public MarkReceivedButton(Module owner)
    {
        super(owner, "Mark Received", "LabPurchasing.window.MarkReceivedWindow.buttonHandler(dataRegionName);");
        setInsertPosition(-1);
        setClientDependencies(ClientDependency.supplierFromModuleName("laboratory"), ClientDependency.supplierFromPath("labpurchasing/window/MarkReceivedWindow.js"));
    }
}
