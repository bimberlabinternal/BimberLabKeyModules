package org.labkey.mcc.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.AbstractRole;

public class MccDataAdminRole extends AbstractRole
{
    public MccDataAdminRole()
    {
        super("MccDataAdmin", "These users can administer data and animal requests", ReadPermission.class, InsertPermission.class, UpdatePermission.class, MccRequestorPermission.class, MccDataAdminPermission.class, MccRequestAdminPermission.class, MccRabReviewPermission.class);
    }

    @Override
    public @NotNull String getDisplayName()
    {
        return "MCC Data Admin";
    }
}
