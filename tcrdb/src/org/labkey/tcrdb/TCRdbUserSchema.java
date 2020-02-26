package org.labkey.tcrdb;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.table.ContainerScopedTable;
import org.labkey.api.ldk.table.SharedDataTable;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;

/**
 * Created by bimber on 6/18/2016.
 */
public class TCRdbUserSchema extends SimpleUserSchema
{
    private TCRdbUserSchema(User user, Container container, DbSchema schema)
    {
        super(TCRdbSchema.NAME, null, user, container, schema);
    }

    public static void register(final Module m)
    {
        final DbSchema dbSchema = TCRdbSchema.getInstance().getSchema();

        DefaultSchema.registerProvider(TCRdbSchema.NAME, new DefaultSchema.SchemaProvider(m)
        {
            public QuerySchema createSchema(final DefaultSchema schema, Module module)
            {
                return new TCRdbUserSchema(schema.getUser(), schema.getContainer(), dbSchema);
            }
        });
    }

    @Override
    @Nullable
    protected TableInfo createWrappedTable(String name, @NotNull TableInfo sourceTable, ContainerFilter cf)
    {
        if (TCRdbSchema.TABLE_LIBRARIES.equalsIgnoreCase(name))
        {
            // TODO: assert cf is null or not default?
            return new SharedDataTable(this, sourceTable).init();
        }
        else if (TCRdbSchema.TABLE_CITE_SEQ_ANTIBODIES.equalsIgnoreCase(name))
        {
            return new ContainerScopedTable<>(this, sourceTable, cf, "antibodyName").init();
        }
        else if (TCRdbSchema.TABLE_CITE_SEQ_PANELS.equalsIgnoreCase(name))
        {
            return new ContainerScopedTable<>(this, sourceTable, cf, "name").init();
        }

        return super.createWrappedTable(name, sourceTable, cf);
    }
}