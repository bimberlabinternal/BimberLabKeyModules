package org.labkey.mcc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.WrappedColumnInfo;
import org.labkey.api.ldk.table.CustomPermissionsTable;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
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

            if (MccSchema.TABLE_REQUEST_SCORE.equalsIgnoreCase(name))
            {
                return addScoreColumns(ret).init();
            }

            return ret.init();
        }

        return super.createWrappedTable(name, schemaTable, cf);
    }

    private CustomPermissionsTable<?> addScoreColumns(CustomPermissionsTable<?> ti)
    {
        if (ti.getColumn("rabReviewStatus") == null)
        {
            SQLFragment sql = new SQLFragment("(SELECT CAST(sum(CASE WHEN m.score IS NULL THEN 0 ELSE 1 END) as varchar) || ' of ' || cast(count(*) as varchar) as expr FROM mcc.requestReviews r WHERE m.requestId = " + ExprColumn.STR_TABLE_ALIAS + ".requestId)");
            ExprColumn newCol = new ExprColumn(ti, "rabReviewStatus", sql, JdbcType.VARCHAR, ti.getColumn("requestId"));

            newCol.setLabel("RAB Review Status");
            newCol.setDisplayColumnFactory(colInfo -> {
                return new DataColumn(colInfo)
                {
                    @Override
                    public boolean isFilterable()
                    {
                        return false;
                    }

                    @Override
                    public boolean isSortable()
                    {
                        return false;
                    }
                };
            });

            ti.addColumn(newCol);

            MutableColumnInfo col = WrappedColumnInfo.wrapAsCopy(ti, FieldKey.fromString("rabReviewStatus"), ti.getColumn("requestId"), "RAB Review Status", null);
            col.setCalculated(true);
        }

        return ti;
    }
}
