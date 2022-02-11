package org.labkey.covidseq;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.ldk.table.ContainerScopedTable;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;

public class CovidseqUserSchema extends SimpleUserSchema
{
    private CovidseqUserSchema(User user, Container container, DbSchema schema)
    {
        super(CovidseqSchema.NAME, null, user, container, schema);
    }

    public static void register(final Module m)
    {
        final DbSchema dbSchema = DbSchema.get(CovidseqSchema.NAME, DbSchemaType.Module);

        DefaultSchema.registerProvider(CovidseqSchema.NAME, new DefaultSchema.SchemaProvider(m)
        {
            @Override
            public QuerySchema createSchema(final DefaultSchema schema, Module module)
            {
                return new CovidseqUserSchema(schema.getUser(), schema.getContainer(), dbSchema);
            }
        });
    }

    @Override
    @Nullable
    protected TableInfo createWrappedTable(String name, @NotNull TableInfo sourceTable, ContainerFilter cf)
    {
        if (CovidseqSchema.TABLE_SAMPLES.equalsIgnoreCase(name))
        {
            ContainerScopedTable<?> ret = new ContainerScopedTable<>(this, sourceTable, cf, "sampleName").init();
            LDKService.get().applyNaturalSort(ret, "samplename");
            LDKService.get().applyNaturalSort(ret, "patientid");

            return(ret);
        }
        else
        {
            return super.createWrappedTable(name, sourceTable, cf);
        }
    }
}
