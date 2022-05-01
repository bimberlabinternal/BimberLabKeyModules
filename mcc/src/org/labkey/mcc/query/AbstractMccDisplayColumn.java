package org.labkey.mcc.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.query.FieldKey;

public abstract class AbstractMccDisplayColumn extends DataColumn
{
    public AbstractMccDisplayColumn(ColumnInfo colInfo)
    {
        super(colInfo);
    }

    protected FieldKey getBoundKey(String... colNames)
    {
        FieldKey ret = null;
        for (String colName : colNames)
        {
            if (ret == null)
            {
                ret = new FieldKey(getBoundColumn().getFieldKey().getParent(), colName);
            }
            else
            {
                ret = ret.append(colName);
            }
        }

        return ret;
    }

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
}
