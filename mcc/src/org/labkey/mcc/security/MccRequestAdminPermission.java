package org.labkey.mcc.security;

import org.labkey.api.security.permissions.AbstractPermission;

public class MccRequestAdminPermission extends AbstractPermission
{
    public MccRequestAdminPermission()
    {
        super("MccRequestAdmin", "This permission allows the user to administer animal requests in the MCC");
    }
}
