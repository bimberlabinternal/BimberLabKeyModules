package org.labkey.sivstudies.query;

import org.apache.logging.log4j.Logger;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.WrappedColumn;
import org.labkey.api.ldk.table.AbstractTableCustomizer;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.study.DatasetTable;
import org.labkey.api.util.logging.LogHelper;

import java.util.ArrayList;
import java.util.List;

public class SivStudiesCustomizer extends AbstractTableCustomizer
{
    public static final String ID_COL = "Id";
    private static final Logger _log = LogHelper.getLogger(SivStudiesCustomizer.class, "Table customization for the SIV Studies module");

    @Override
    public void customize(TableInfo tableInfo)
    {
        if (tableInfo instanceof DatasetTable ds)
        {
            performDatasetCustomization(ds);
        }
    }

    public void performDatasetCustomization(DatasetTable ds)
    {
        if (ds instanceof AbstractTableInfo ati)
        {
            _log.debug("Customizing dataset: {}", ds.getName());

            if (!ds.getDataset().isDemographicData())
            {
                appendAgeAtTimeCol(ds.getUserSchema(), ati, "date");
            }

            if ("demographics".equalsIgnoreCase(ds.getName()))
            {
                appendDemographicsColumns(ati);
            }
        }
        else
        {
            _log.error("Expected DatasetTable to be instanceof AbstractTableInfo: " + ds.getName());
        }
    }

    private ColumnInfo getPkCol(TableInfo ti)
    {
        List<ColumnInfo> pks = ti.getPkColumns();
        return (pks.size() != 1) ? null : pks.get(0);
    }

    private void appendAgeAtTimeCol(UserSchema demographicsSchema, AbstractTableInfo ds, final String dateColName)
    {
        String name = "ageAtTime";
        if (ds.getColumn(name, false) != null)
            return;

        final ColumnInfo pkCol = getPkCol(ds);
        if (pkCol == null)
            return;

        final ColumnInfo idCol = ds.getColumn(ID_COL);
        if (idCol == null)
            return;

        if (ds.getColumn(dateColName) == null)
            return;

        final String targetSchemaName = ds.getUserSchema().getName();
        final Container targetSchemaContainer = ds.getUserSchema().getContainer();
        final User u = ds.getUserSchema().getUser();
        final String schemaName = ds.getPublicSchemaName();
        final String queryName = ds.getName();
        final String demographicsPath = demographicsSchema.getContainer().getPath();

        WrappedColumn col = new WrappedColumn(pkCol, name);
        col.setLabel("Age At The Time");
        col.setReadOnly(true);
        col.setIsUnselectable(true);
        col.setUserEditable(false);
        col.setFk(new LookupForeignKey(){
            @Override
            public TableInfo getLookupTableInfo()
            {
                String name = queryName + "_ageAtTime";
                UserSchema targetSchema = ds.getUserSchema().getDefaultSchema().getUserSchema(targetSchemaName);
                QueryDefinition qd = QueryService.get().createQueryDef(u, targetSchemaContainer, targetSchema, name);
                qd.setSql("SELECT\n" +
                        "c." + pkCol.getFieldKey().toSQLString() + ",\n" +
                        "\n" +
                        "CAST(\n" +
                        "CASE\n" +
                        "WHEN d.birth is null or c." + dateColName + " is null\n" +
                        "  THEN null\n" +
                        "WHEN (d.death IS NOT NULL AND d.death < c." + dateColName + ") THEN\n" +
                        " ROUND(CONVERT(age_in_months(d.birth, d.death), DOUBLE) / 12, 1)\n" +
                        "ELSE\n" +
                        "  ROUND(CONVERT(age_in_months(d.birth, CAST(c." + dateColName + " as DATE)), DOUBLE) / 12, 1)\n" +
                        "END AS float) as AgeAtTime,\n" +
                        "\n" +

                        "CAST(\n" +
                        "CASE\n" +
                        "WHEN d.birth is null or c." + dateColName + " is null\n" +
                        "  THEN null\n" +
                        "WHEN (d.death IS NOT NULL AND d.death < c." + dateColName + ") THEN\n" +
                        " ROUND(CONVERT(timestampdiff('SQL_TSI_DAY', d.birth, d.death), DOUBLE) / 365.25, 2)\n" +
                        "ELSE\n" +
                        "  ROUND(CONVERT(timestampdiff('SQL_TSI_DAY', d.birth, CAST(c." + dateColName + " as DATE)), DOUBLE) / 365.25, 2)\n" +
                        "END AS float) as AgeAtTimeYears,\n" +
                        "\n" +
                        "CAST(\n" +
                        "CASE\n" +
                        "WHEN d.birth is null or c." + dateColName + " is null\n" +
                        "  THEN null\n" +
                        "WHEN (d.death IS NOT NULL AND d.death < c." + dateColName + ") THEN\n" +
                        " floor(age(d.birth, d.death))\n" +
                        "ELSE\n" +
                        "  floor(age(d.birth, CAST(c." + dateColName + " as DATE)))\n" +
                        "END AS float) as AgeAtTimeYearsRounded,\n" +
                        "\n" +
                        "CAST(\n" +
                        "CASE\n" +
                        "WHEN d.birth is null or c." + dateColName + " is null\n" +
                        "  THEN null\n" +
                        "WHEN (d.death IS NOT NULL AND d.death < c." + dateColName + ") THEN\n" +
                        "  CONVERT(TIMESTAMPDIFF('SQL_TSI_DAY',d.birth, d.death), INTEGER)\n" +
                        "ELSE\n" +
                        "  CONVERT(TIMESTAMPDIFF('SQL_TSI_DAY',d.birth, CAST(c." + dateColName + " AS DATE)), INTEGER)\n" +
                        "END AS float) as AgeAtTimeDays,\n" +
                        "\n" +
                        "CAST(\n" +
                        "CASE\n" +
                        "WHEN d.birth is null or c." + dateColName + " is null\n" +
                        "  THEN null\n" +
                        "WHEN (d.death IS NOT NULL AND d.death < c." + dateColName + ") THEN\n" +
                        "  CONVERT(age_in_months(d.birth, d.death), INTEGER)\n" +
                        "ELSE\n" +
                        "  CONVERT(age_in_months(d.birth, CAST(c." + dateColName + " AS DATE)), INTEGER)\n" +
                        "END AS float) as AgeAtTimeMonths,\n" +
                        "FROM \"" + schemaName + "\".\"" + queryName + "\" c " +
                        "LEFT JOIN \"" + demographicsPath + "\".study.demographics d ON (d.Id = c." + idCol.getFieldKey().toSQLString() + ")"
                );
                qd.setIsTemporary(true);

                List<QueryException> errors = new ArrayList<>();
                TableInfo ti = qd.getTable(errors, true);
                if (!errors.isEmpty())
                {
                    _log.warn("Error creating age at time lookup table for: " + schemaName + "." + queryName + " in container: " + targetSchema.getContainer().getPath());
                    for (QueryException e : errors)
                    {
                        _log.warn(e.getMessage(), e);
                    }
                }

                if (ti != null)
                {
                    ((BaseColumnInfo)ti.getColumn(pkCol.getName())).setHidden(true);
                    ((BaseColumnInfo)ti.getColumn(pkCol.getName())).setKeyField(true);
                }

                return ti;
            }
        });

        ds.addColumn(col);
    }

    private void appendDemographicsColumns(AbstractTableInfo demographicsTable)
    {
        if (demographicsTable.getColumn("mhcGenotypes") == null)
        {
            BaseColumnInfo colInfo = getWrappedIdCol(demographicsTable.getUserSchema(), "demographicsMHC", demographicsTable, "mhcGenotypes");
            colInfo.setLabel("MHC Genotypes");
            demographicsTable.addColumn(colInfo);
        }
    }

    private void appendPvlColumns(AbstractTableInfo ti, ColumnInfo subjectCol, ColumnInfo dateCol)
    {
        Container target = ti.getUserSchema().getContainer().isWorkbookOrTab() ? ti.getUserSchema().getContainer().getParent() : ti.getUserSchema().getContainer();

//            TableInfo data = schema.createDataTable(null);
//            String tableName = data.getDomain().getStorageTableName();
//
//            String name = "viralLoad";
//            if (ti.getColumn(name) == null)
//            {
//                SQLFragment sql = new SQLFragment("(SELECT avg(viralLoad) as expr FROM assayresult." + tableName + " t WHERE t.subjectId = " + ExprColumn.STR_TABLE_ALIAS + "." + subjectCol.getName() + " AND CAST(t.date AS DATE) = CAST(" + ExprColumn.STR_TABLE_ALIAS + "." + dateCol.getName() + " AS DATE))");
//                ExprColumn newCol = new ExprColumn(ti, name, sql, JdbcType.DOUBLE, subjectCol, dateCol);
//                newCol.setDescription("Displays the viral load from this timepoint, if present");
//                newCol.setLabel("Viral Load (copies/mL)");
//                ti.addColumn(newCol);
//            }
    }

    private BaseColumnInfo getWrappedIdCol(UserSchema targetQueryUserSchema, String targetQueryName, AbstractTableInfo demographicsTable, String colName)
    {
        WrappedColumn col = new WrappedColumn(demographicsTable.getColumn(ID_COL), colName);
        col.setReadOnly(true);
        col.setIsUnselectable(true);
        col.setUserEditable(false);
        col.setFk(new QueryForeignKey(demographicsTable.getUserSchema(), null, targetQueryUserSchema, null, targetQueryName, ID_COL, ID_COL));

        return col;
    }
}
