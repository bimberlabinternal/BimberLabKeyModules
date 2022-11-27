package org.labkey.labpurchasing;

import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.table.AbstractTableCustomizer;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;

public class LabPurchasingCustomizer extends AbstractTableCustomizer
{
    @Override
    public void customize(TableInfo tableInfo)
    {
        if (LabPurchasingSchema.TABLE_PURCHASES.equalsIgnoreCase(tableInfo.getName()))
        {
            customizePurchases((AbstractTableInfo)tableInfo);
        }
        else if (LabPurchasingSchema.TABLE_REFERENCE_ITEMS.equalsIgnoreCase(tableInfo.getName()))
        {
            customizeReferenceItems((AbstractTableInfo)tableInfo);
        }
    }

    private void customizePurchases(AbstractTableInfo ti)
    {
        ti.setInsertURL(DetailsURL.fromString("labpurchasing/order.view"));
        ti.setImportURL(AbstractTableInfo.LINK_DISABLER);

        addItemAndNumber(ti);
    }

    private void customizeReferenceItems(AbstractTableInfo ti)
    {
        addItemAndNumber(ti);
    }

    private void addItemAndNumber(AbstractTableInfo ti)
    {
        String name = "itemAndNumber";
        if (ti.getColumn(name) == null)
        {
            SQLFragment sql = new SQLFragment("CASE WHEN " + ExprColumn.STR_TABLE_ALIAS + ".itemNumber IS NULL THEN " + ExprColumn.STR_TABLE_ALIAS + ".itemName ELSE " + ti.getSqlDialect().concatenate(ExprColumn.STR_TABLE_ALIAS + ".itemName", "' (#'", ExprColumn.STR_TABLE_ALIAS + ".itemNumber", "')'") + " END");
            ExprColumn col = new ExprColumn(ti, name, sql, JdbcType.VARCHAR, ti.getColumn("itemName"), ti.getColumn("itemNumber"));
            col.setLabel("Item And Number");
            col.setHidden(true);
            ti.addColumn(col);
        }
    }
}
