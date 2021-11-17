package org.labkey.mcc.security;

import org.labkey.api.security.permissions.AbstractPermission;

public class MccDataPermission extends AbstractPermission
{
    public MccDataPermission()
    {
        super("MccDataAdmin", "This permission allows the user to administer animal data in the MCC");
    }
}
