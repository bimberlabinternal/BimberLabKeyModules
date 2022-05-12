package org.labkey.mcc.security;

import org.labkey.api.security.permissions.AbstractPermission;

public class MccRabReviewPermission extends AbstractPermission
{
    public MccRabReviewPermission()
    {
        super("MccRabReview", "This permission allows the user to perform tasked related to RAB review in the MCC");
    }
}
