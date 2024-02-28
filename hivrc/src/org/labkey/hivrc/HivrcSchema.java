package org.labkey.hivrc;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.dialect.SqlDialect;

public class HivrcSchema
{
    public static final String LABORATORY = "laboratory";
    public static final String TABLE_WORKBOOKS = "workbooks";
    public static final String TABLE_WORKBOOK_TAGS = "workbook_tags";

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

    public DbSchema getLaboratorySchema()
    {
        return DbSchema.get(LABORATORY, DbSchemaType.Module);
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }
}
