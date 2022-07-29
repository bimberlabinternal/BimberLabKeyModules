package org.labkey.mcc.query;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.WrappedColumn;
import org.labkey.api.ldk.table.AbstractTableCustomizer;
import org.labkey.api.query.ExprColumn;
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
                customizeDemographics((AbstractTableInfo)table);
            }
            else if (matches(table, "study", "weight"))
            {
                customizeWeight((AbstractTableInfo)table);
            }
        }
    }

    private void customizeDemographics(AbstractTableInfo ti)
    {
        addMccAlias(ti, "dam", "damMccAlias", "Dam MCC Alias");
        addMccAlias(ti, "sire", "sireMccAlias", "Sire MCC Alias");
    }

    private void customizeWeight(AbstractTableInfo ti)
    {
        String name = "weightGrams";
        if (ti.getColumn(name) == null)
        {
            SQLFragment sql = new SQLFragment("CASE WHEN " + ExprColumn.STR_TABLE_ALIAS + ".weight IS NULL THEN NULL ELSE (" + ExprColumn.STR_TABLE_ALIAS + ".weight / 1000) END");
            ExprColumn newCol = new ExprColumn(ti, name, sql, JdbcType.DOUBLE, ti.getColumn("weight"));
            newCol.setLabel("Weight (g)");

            ti.addColumn(newCol);
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
        if (ti.getColumn(sourceCol) == null)
        {
            return;
        }

        if (ti.getColumn(name) == null)
        {
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
            fk.addJoin(FieldKey.fromString(sourceCol), "subjectname", false);
            ci.setFk(fk);
            ci.setUserEditable(false);
            ci.setLabel(label);
            ti.addColumn(ci);
        }
    }
}