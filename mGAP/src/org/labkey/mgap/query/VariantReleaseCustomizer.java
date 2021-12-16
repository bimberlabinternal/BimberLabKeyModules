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
            addVariantCols((AbstractTableInfo)tableInfo);
        }
    }

    public void addVariantCols(AbstractTableInfo ti)
    {
        String colName = "hasSignificantVariants";
        if (ti.getColumn(colName) == null)
        {
            ExprColumn col = new ExprColumn(ti, colName, new SQLFragment("(CASE WHEN (exists " +
                    "(select l.rowid from mgap.variantList l WHERE l.releaseId = " + ExprColumn.STR_TABLE_ALIAS + ".objectId) " +
                    ") THEN " + ti.getSqlDialect().getBooleanTRUE() + " ELSE " + ti.getSqlDialect().getBooleanFALSE() + " END)"), JdbcType.BOOLEAN, ti.getColumn("objectId"));
            col.setLabel("Has Significant Variants?");
            col.setReadOnly(true);
            col.setHidden(true);
            col.setUserEditable(false);
            ti.addColumn(col);
        }

        String primaryTrack = "primaryTrack";
        if (ti.getColumn(primaryTrack) == null)
        {
            ExprColumn col = new ExprColumn(ti, primaryTrack, new SQLFragment("(" +
                    "select jf.objectid from jbrowse.jsonfiles jf " +
                    " JOIN jbrowse.database_members dm ON (jf.objectid = dm.jsonfile) " +
                    " JOIN jbrowse.databases d ON (d.objectid = dm." + ti.getSqlDialect().quoteIdentifier("database") + ") " +
                    " WHERE jf.outputfile = " + ExprColumn.STR_TABLE_ALIAS + ".vcfId AND d.objectid = " + ExprColumn.STR_TABLE_ALIAS + ".jbrowseId)"
                    ), JdbcType.VARCHAR, ti.getColumn("jbrowseId"), ti.getColumn("vcfId"));
            col.setLabel("Primary Variant Track");
            col.setReadOnly(true);
            col.setHidden(true);
            col.setUserEditable(false);
            ti.addColumn(col);
        }
    }
}
