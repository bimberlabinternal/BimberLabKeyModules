package org.labkey.mcc.query;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.table.AbstractTableCustomizer;

public class MccEhrCustomizer extends AbstractTableCustomizer
{
    private static final Logger _log = LogManager.getLogger(MccEhrCustomizer.class);

    public MccEhrCustomizer()
    {

    }

    @Override
    public void customize(TableInfo table)
    {
        if (table instanceof AbstractTableInfo)
        {
            if (matches(table, "study", "Animal"))
            {
                customizeAnimalTable((AbstractTableInfo)table);
            }
        }
    }

    private void customizeAnimalTable(AbstractTableInfo ti)
    {
        for (String colName : new String[]{"MostRecentArrival", "numRoommates", "MostRecentDeparture", "curLocation", "lastHousing", "weightChange", "CageClass", "MhcStatus"})
        {
            ColumnInfo ci = ti.getColumn(colName);
            if (ci != null)
            {
                ti.removeColumn(ci);
            }
        }
    }
}