package org.labkey.hivrc;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.dialect.SqlDialect;

public class HivrcSchema
{
    private static final HivrcSchema _instance = new HivrcSchema();
    public static final String NAME = "hivrc";

    public static HivrcSchema getInstance()
    {
        return _instance;
    }

    private HivrcSchema()
    {

    }

    public DbSchema getSchema()
    {
        return DbSchema.get(NAME, DbSchemaType.Module);
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }
}
