package org.labkey.mcc.query;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.WrappedColumn;
import org.labkey.api.ldk.table.AbstractTableCustomizer;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.mcc.MccSchema;

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
            else if (matches(table, "study", "demographics"))
            {
                addMccAlias((AbstractTableInfo)table, "dam", "damMccAlias", "Dam MCC Alias");
                addMccAlias((AbstractTableInfo)table, "sire", "sireMccAlias", "Sire MCC Alias");
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

        addMccAlias(ti, "Id", "mccAlias", "MCC Alias");
    }

    private void addMccAlias(AbstractTableInfo ti, String sourceCol, String name, String label)
    {
        if (ti.getColumn(name) == null)
        {
            if (ti.getColumn(sourceCol) == null)
            {
                _log.error("Unable to find column: " + sourceCol + " for table: " + ti.getName());
                return;
            }

            WrappedColumn ci = new WrappedColumn(ti.getColumn(sourceCol), name);
            ci.setFieldKey(FieldKey.fromParts(name));
            final UserSchema us = getUserSchema(ti, MccSchema.NAME);
            LookupForeignKey fk = new LookupForeignKey("subjectname")
            {
                @Override
                public TableInfo getLookupTableInfo()
                {
                    return us.getTable(MccSchema.TABLE_ANIMAL_MAPPING);
                }
            };
            fk.addJoin(FieldKey.fromString("Id"), "subjectname", false);

            ci.setFk(fk);
            ci.setUserEditable(false);
            ci.setLabel(label);
            ti.addColumn(ci);
        }
    }
}