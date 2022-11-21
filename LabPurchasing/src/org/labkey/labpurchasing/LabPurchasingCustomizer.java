package org.labkey.labpurchasing;

import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.table.AbstractTableCustomizer;
import org.labkey.api.query.DetailsURL;

public class LabPurchasingCustomizer extends AbstractTableCustomizer
{
    @Override
    public void customize(TableInfo tableInfo)
    {
        if (LabPurchasingSchema.TABLE_PURCHASES.equalsIgnoreCase(tableInfo.getName()))
        {
            customizePurchases((AbstractTableInfo)tableInfo);
        }
    }

    private void customizePurchases(AbstractTableInfo ti)
    {
        ti.setInsertURL(DetailsURL.fromString("labpurchasing/order.view"));
        ti.setImportURL(AbstractTableInfo.LINK_DISABLER);
    }
}
