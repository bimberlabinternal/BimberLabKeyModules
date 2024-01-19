package org.labkey.mcc.security;

import org.labkey.api.security.permissions.AbstractPermission;

/**
 * This is required to view the data from request_scores and other approval steps.
 */
public class MccViewRequestsPermission extends AbstractPermission
{
    public MccViewRequestsPermission()
    {
        super("MccFinalReview", "This permission is required to enter the final decision on animal requests in the MCC");
    }
}
