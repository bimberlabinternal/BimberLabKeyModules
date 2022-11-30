package org.labkey.labpurchasing;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.laboratory.query.ContainerIncrementingTable;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;

public class LabPurchasingUserSchema extends SimpleUserSchema
{
    private LabPurchasingUserSchema(User user, Container container, DbSchema schema)
    {
        super(LabPurchasingSchema.NAME, null, user, container, schema);
    }

    public static void register(final Module m)
    {
        final DbSchema dbSchema = DbSchema.get(LabPurchasingSchema.NAME, DbSchemaType.Module);

        DefaultSchema.registerProvider(LabPurchasingSchema.NAME, new DefaultSchema.SchemaProvider(m)
        {
            @Override
            public QuerySchema createSchema(final DefaultSchema schema, Module module)
            {
                return new LabPurchasingUserSchema(schema.getUser(), schema.getContainer(), dbSchema);
            }
        });
    }

    private SimpleTable getPurchasesTable(String name, @NotNull TableInfo schematable, ContainerFilter cf)
    {
        return new ContainerIncrementingTable(this, schematable, cf, "purchaseId").init();
    }

    @Override
    @Nullable
    protected TableInfo createWrappedTable(String name, @NotNull TableInfo sourceTable, ContainerFilter cf)
    {
        if (LabPurchasingSchema.TABLE_PURCHASES.equalsIgnoreCase(name))
            return getPurchasesTable(name, sourceTable, cf);
        else
            return super.createWrappedTable(name, sourceTable, cf);
    }
}
