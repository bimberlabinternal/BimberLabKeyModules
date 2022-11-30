package org.labkey.labpurchasing.buttons;

import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.Module;
import org.labkey.api.view.template.ClientDependency;

public class EnterOrderInfoButton extends SimpleButtonConfigFactory
{
    public EnterOrderInfoButton(Module owner)
    {
        super(owner, "Order Items", "LabPurchasing.buttons.purchaseItems(dataRegionName);");
        setInsertPosition(-1);
        setClientDependencies(ClientDependency.supplierFromModuleName("laboratory"), ClientDependency.supplierFromPath("labpurchasing/GridButtons.js"));
    }
}
