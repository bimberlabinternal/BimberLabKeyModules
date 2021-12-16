package org.labkey.mcc.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.permissions.AbstractPermission;

import java.util.Collection;

public class MccRequestorPermission extends AbstractPermission
{
    public MccRequestorPermission()
    {
        super("MccRequestor", "This permission allows the user to submit animal requests in the MCC");
    }
}
