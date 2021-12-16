package org.labkey.mcc.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.permissions.AbstractPermission;

import java.util.Collection;

public class MccRequestAdminPermission extends AbstractPermission
{
    public MccRequestAdminPermission()
    {
        super("MccRequestAdmin", "This permission allows the user to administer animal requests in the MCC");
    }
}
