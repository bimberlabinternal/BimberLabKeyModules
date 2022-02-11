package org.labkey.mcc.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.AbstractRole;

public class MccRequesterRole extends AbstractRole
{
    public MccRequesterRole()
    {
        super("MCCRequestor", "These users can submit animal requests", ReadPermission.class, UpdatePermission.class, InsertPermission.class, MccRequestorPermission.class);
    }

    @Override
    public @NotNull String getDisplayName()
    {
        return "MCC Requestor";
    }
}
