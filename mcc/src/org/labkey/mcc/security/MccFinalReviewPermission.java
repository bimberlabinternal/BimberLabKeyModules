package org.labkey.mcc.security;

import org.labkey.api.security.permissions.AbstractPermission;

public class MccFinalReviewPermission extends AbstractPermission
{
    public MccFinalReviewPermission()
    {
        super("MccFinalReview", "This permission is required to enter the final decision on animal requests in the MCC");
    }
}
