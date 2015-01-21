package org.labkey.variantdb.security;

import org.labkey.api.security.permissions.AbstractPermission;

/**
 * Created by bimber on 1/4/2015.
 */
public class VariantManagerPermission extends AbstractPermission
{
    public VariantManagerPermission()
    {
        super("VariantManagerPermission", "This allows users to the reference variant list and other site-wide resources");
    }
}