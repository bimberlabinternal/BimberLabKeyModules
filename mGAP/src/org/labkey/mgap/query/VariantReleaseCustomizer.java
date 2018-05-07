package org.labkey.mgap.query;

import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.query.ExprColumn;

public class VariantReleaseCustomizer implements TableCustomizer
{
    @Override
    public void customize(TableInfo tableInfo)
    {
        LDKService.get().getDefaultTableCustomizer().customize(tableInfo);

        if (tableInfo instanceof AbstractTableInfo)
        {
            addVariantCol((AbstractTableInfo)tableInfo);
        }
    }

    public void addVariantCol(AbstractTableInfo ti)
    {
        String colName = "hasSignificantVariants";
        if (ti.getColumn(colName) != null)
        {
            return;
        }

        ExprColumn col = new ExprColumn(ti, colName, new SQLFragment("(CASE WHEN (exists " +
                "(select l.rowid from mgap.variantList l WHERE l.releaseId = " + ExprColumn.STR_TABLE_ALIAS + ".objectId) " +
                ") THEN " + ti.getSqlDialect().getBooleanTRUE() + " ELSE " + ti.getSqlDialect().getBooleanFALSE() + " END)"), JdbcType.BOOLEAN, ti.getColumn("objectId"));
        col.setLabel("Has Significant Variants?");
        col.setReadOnly(true);
        col.setHidden(true);
        col.setUserEditable(false);
        ti.addColumn(col);
    }
}
