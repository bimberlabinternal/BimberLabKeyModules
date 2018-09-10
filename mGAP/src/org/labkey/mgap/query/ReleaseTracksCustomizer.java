package org.labkey.mgap.query;

import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.query.ExprColumn;

public class ReleaseTracksCustomizer implements TableCustomizer
{
    @Override
    public void customize(TableInfo tableInfo)
    {
        LDKService.get().getDefaultTableCustomizer().customize(tableInfo);

        if (tableInfo instanceof AbstractTableInfo)
        {
            addTrackCol((AbstractTableInfo)tableInfo);
        }
    }

    public void addTrackCol(AbstractTableInfo ti)
    {
        String colName = "jbrowseTracks";
        if (ti.getColumn(colName) != null)
        {
            return;
        }

        ExprColumn col = new ExprColumn(ti, colName, new SQLFragment(
            "(select ").append(ti.getSqlDialect().getGroupConcat(new SQLFragment("(CASE WHEN j.trackId IS NOT NULL THEN 'track-' + CAST(j.trackId as varchar) WHEN j.outputfile IS NOT NULL THEN 'data-' + cast(j.outputfile as varchar) ELSE NULL END)"), true, true, "','")).append(new SQLFragment(" from mgap.variantCatalogReleases l " +
                " JOIN mgap.tracksPerRelease tr ON (tr.releaseId = " + ExprColumn.STR_TABLE_ALIAS + ".releaseId AND tr.isprimarytrack = 1)" +
                " JOIN jbrowse." + ti.getSqlDialect().makeLegalIdentifier("databases") + " d ON (l.jbrowseId = d.objectid)" +
                " JOIN sequenceanalysis.reference_libraries rl on (d.libraryId = rl.rowid)" +
                " JOIN sequenceanalysis.reference_library_tracks rlt on (rl.rowid = rlt.library_id)" +

                " JOIN jbrowse.database_members dm ON (dm." + ti.getSqlDialect().makeLegalIdentifier("database") + " = d.objectid)" +
                " JOIN jbrowse.jsonfiles j ON (" +
                    "(dm.jsonfile = j.objectid AND (" + ExprColumn.STR_TABLE_ALIAS + ".vcfId = j.outputfile OR tr.vcfId = j.outputfile))" +
                    " OR " +
                    " (rlt.rowid = j.trackid AND j.trackJson like '%\"visibleByDefault\":\"true\"%')" +
                " )" +
                " WHERE l.objectId = " + ExprColumn.STR_TABLE_ALIAS + ".releaseId" +
                " )")), JdbcType.VARCHAR, ti.getColumn("vcfId"));

        col.setLabel("JBrowse Tracks");
        col.setReadOnly(true);
        col.setHidden(true);
        col.setUserEditable(false);
        ti.addColumn(col);
    }
}
