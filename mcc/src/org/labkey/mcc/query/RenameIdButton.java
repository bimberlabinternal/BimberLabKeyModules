package org.labkey.mcc.query;

import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.mcc.MccModule;
import org.labkey.mcc.security.MccDataAdminPermission;

import java.util.List;

public class RenameIdButton extends SimpleButtonConfigFactory
{
    public RenameIdButton()
    {
        super(ModuleLoader.getInstance().getModule(MccModule.class), "Rename ID", "MCC.window.RenameIdWindow.buttonHandler(dataRegionName);", List.of(ClientDependency.supplierFromPath("mcc/window/RenameIdWindow.js")));
        setPermission(MccDataAdminPermission.class);
    }
}
