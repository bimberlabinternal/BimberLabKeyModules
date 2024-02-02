package org.labkey.mcc.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.AbstractRole;

public class MccRabReviewerRole extends AbstractRole
{
    public MccRabReviewerRole()
    {
        super("MccRabReviewer", "These users can enter RAB reviews", ReadPermission.class, InsertPermission.class, UpdatePermission.class, MccRequestorPermission.class, MccViewRequestsPermission.class, MccRabReviewPermission.class);
    }

    @Override
    public @NotNull String getDisplayName()
    {
        return "MCC RAB Reviewer";
    }
}
