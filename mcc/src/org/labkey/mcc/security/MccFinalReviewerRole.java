package org.labkey.mcc.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.AbstractRole;

public class MccFinalReviewerRole extends AbstractRole
{
    public MccFinalReviewerRole()
    {
        super("MccFinalReviewer", "These users can enter the final reviews for MCC requests", ReadPermission.class, InsertPermission.class, UpdatePermission.class, MccRequestorPermission.class, MccRabReviewPermission.class, MccFinalReviewPermission.class);
    }

    @Override
    public @NotNull String getDisplayName()
    {
        return "MCC Final Reviewer";
    }
}
