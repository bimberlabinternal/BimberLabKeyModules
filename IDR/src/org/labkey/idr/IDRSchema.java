package org.labkey.idr;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.dialect.SqlDialect;

public class IDRSchema
{
    private static final IDRSchema _instance = new IDRSchema();
    public static final String NAME = "idr";

    public static IDRSchema getInstance()
    {
        return _instance;
    }

    private IDRSchema()
    {
        // private constructor to prevent instantiation from
        // outside this class: this singleton should only be
        // accessed via org.labkey.idr.IDRSchema.getInstance()
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
