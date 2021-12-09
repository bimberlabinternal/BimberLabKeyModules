package org.labkey.mcc.query;

import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.query.ExprColumn;
import org.labkey.mcc.MccManager;

public class UserRequestCustomizer implements TableCustomizer
{
    @Override
    public void customize(TableInfo tableInfo)
    {
        LDKService.get().getDefaultTableCustomizer().customize(tableInfo);

        if (tableInfo instanceof AbstractTableInfo)
        {
            addUserCol((AbstractTableInfo)tableInfo);
        }
    }

    public void addUserCol(AbstractTableInfo ti)
    {
        String colName = "hasAccess";
        if (ti.getColumn(colName) != null)
        {
            return;
        }

        ExprColumn col = new ExprColumn(ti, colName, new SQLFragment("(CASE WHEN (exists (" +
                "select u.rowid from mcc.userrequests u " +
                "left join core.Members m " +
                "on (u.userid = m.UserId) " +
                "WHERE m.GroupId = (select p.UserId from core.Principals p WHERE p.type = 'g' AND p.Name = '" + MccManager.MCC_GROUP_NAME + "') AND u.rowid = " + ExprColumn.STR_TABLE_ALIAS + ".rowid " +
                ")) THEN " + ti.getSqlDialect().getBooleanTRUE() + " ELSE " + ti.getSqlDialect().getBooleanFALSE() + " END)"), JdbcType.BOOLEAN, ti.getColumn("userId"));
        col.setLabel("Has MCC Access?");
        col.setReadOnly(true);
        col.setIsUnselectable(true);
        col.setUserEditable(false);
        ti.addColumn(col);
    }
}
