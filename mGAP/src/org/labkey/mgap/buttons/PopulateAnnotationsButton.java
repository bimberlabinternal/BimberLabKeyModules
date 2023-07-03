package org.labkey.mgap.buttons;

import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.Module;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.permissions.AdminPermission;

public class PopulateAnnotationsButton extends SimpleButtonConfigFactory
{
    public PopulateAnnotationsButton(Module owner)
    {
        super(owner, "Create mGAP Release", DetailsURL.fromString("/mgap/updateAnnotations.view"));
        setPermission(AdminPermission.class);
    }
}
