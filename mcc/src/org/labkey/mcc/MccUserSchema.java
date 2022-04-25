package org.labkey.mcc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.table.CustomPermissionsTable;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.mcc.security.MccRequestAdminPermission;

public class MccUserSchema extends SimpleUserSchema
{
    public MccUserSchema(User user, Container container, DbSchema dbschema)
    {
        super(MccSchema.NAME, "MCC-specific tables, such as requests", user, container, dbschema);
    }

    @Override
    @Nullable
    protected TableInfo createWrappedTable(String name, @NotNull TableInfo schemaTable, ContainerFilter cf)
    {
        if (MccSchema.TABLE_REQUEST_REVIEWS.equalsIgnoreCase(name) || MccSchema.TABLE_REQUEST_SCORE.equalsIgnoreCase(name))
        {
            CustomPermissionsTable<?> ret = new CustomPermissionsTable<>(this, schemaTable, cf);
            ret.addPermissionMapping(ReadPermission.class, MccRequestAdminPermission.class);
            ret.addPermissionMapping(InsertPermission.class, MccRequestAdminPermission.class);
            ret.addPermissionMapping(UpdatePermission.class, MccRequestAdminPermission.class);
            ret.addPermissionMapping(DeletePermission.class, MccRequestAdminPermission.class);

            return ret.init();
        }

        return super.createWrappedTable(name, schemaTable, cf);
    }
}
