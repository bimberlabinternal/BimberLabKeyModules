package org.labkey.sivstudies.query;

import org.apache.logging.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.WrappedColumn;
import org.labkey.api.ldk.table.AbstractTableCustomizer;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.studies.StudiesService;
import org.labkey.api.studies.query.ResultsOORDisplayColumn;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.DatasetTable;
import org.labkey.api.util.logging.LogHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SivStudiesCustomizer extends AbstractTableCustomizer
{
    private static final Logger _log = LogHelper.getLogger(SivStudiesCustomizer.class, "Table customization for the SIV Studies module");

    public static final String ID_COL = "Id";
    public static final String DATE_COL = "Date";

    @Override
    public void customize(TableInfo tableInfo)
    {
        StudiesService.get().getStudiesTableCustomizer().customize(tableInfo);
        if (tableInfo instanceof DatasetTable ds)
        {
            performDatasetCustomization(ds);
        }
        else if ("chemistryPivot".equalsIgnoreCase(tableInfo.getName()) | "hematologyPivot".equalsIgnoreCase(tableInfo.getName()))
        {
            if (tableInfo instanceof AbstractTableInfo ati)
            {
                appendDemographicsColumns(ati);
            }
        }
    }

    public void performDatasetCustomization(DatasetTable ds)
    {
        if (ds instanceof AbstractTableInfo ati)
        {
            _log.debug("Customizing dataset: {}", ds.getName());

            if (!ds.getDataset().isDemographicData())
            {
                appendAgeAtTimeCol(ds.getUserSchema(), ati, ID_COL, DATE_COL);
                appendPvlColumns(ds, ID_COL, DATE_COL);
                appendSivChallengeColumns(ati, ID_COL, DATE_COL);
                appendArtColumns(ds, ID_COL, DATE_COL);
            }

            appendDemographicsColumns(ati);

            if ("viralLoads".equalsIgnoreCase(ds.getName()))
            {
                customizeViralLoads(ati);
            }
        }
        else
        {
            _log.error("Expected DatasetTable to be instanceof AbstractTableInfo: " + ds.getName());
        }
    }

    private ColumnInfo getPkCol(TableInfo ti)
    {
        Set<String> pks = new CaseInsensitiveHashSet(ti.getPkColumnNames());
        if (pks.size() == 1)
        {
            return ti.getColumn(pks.iterator().next());
        }
        else if (pks.contains("lsid"))
        {
            return ti.getColumn("lsid");
        }
        else if (pks.contains("objectId"))
        {
            return ti.getColumn("objectId");
        }
        else if (pks.contains("Id"))
        {
            return ti.getColumn("Id");
        }
        else if (pks.contains("subjectId"))
        {
            return ti.getColumn("subjectId");
        }

        return null;
    }

    private void appendAgeAtTimeCol(UserSchema demographicsSchema, AbstractTableInfo ds, final String subjectColName, final String dateColName)
    {
        String name = "ageAtTime";
        if (ds.getColumn(name, false) != null)
            return;

        final ColumnInfo pkCol = getPkCol(ds);
        if (pkCol == null)
            return;

        final ColumnInfo idCol = ds.getColumn(subjectColName);
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
        col.setLabel("Age At Time");
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

                    ((BaseColumnInfo)ti.getColumn("AgeAtTime")).setLabel("Age At Time (Years)");
                    ((BaseColumnInfo)ti.getColumn("AgeAtTimeDays")).setLabel("Age At Time (Days)");
                    ((BaseColumnInfo)ti.getColumn("AgeAtTimeMonths")).setLabel("Age At Time (Months)");
                    ((BaseColumnInfo)ti.getColumn("AgeAtTimeYearsRounded")).setLabel("Age At Time (Years, Rounded)");
                }

                return ti;
            }
        });

        ds.addColumn(col);
    }

    private void appendDemographicsColumns(AbstractTableInfo parentTable)
    {
        if (parentTable.getColumn("mhcGenotypes") == null)
        {
            BaseColumnInfo colInfo = getWrappedIdCol(parentTable.getUserSchema(), "demographicsMHC", parentTable, "mhcGenotypes");
            colInfo.setLabel("MHC Genotypes");
            parentTable.addColumn(colInfo);
        }

        if (parentTable.getColumn("projects") == null)
        {
            BaseColumnInfo colInfo = getWrappedIdCol(parentTable.getUserSchema(), "demographicsProjects", parentTable, "projects");
            colInfo.setLabel("Project Summary");
            parentTable.addColumn(colInfo);
        }

        if (parentTable.getColumn("immunizations") == null)
        {
            BaseColumnInfo colInfo = getWrappedIdCol(parentTable.getUserSchema(), "demographicsImmunizations", parentTable, "immunizations");
            colInfo.setLabel("Immunization Summary");
            parentTable.addColumn(colInfo);
        }

        if (parentTable.getColumn("outcomes") == null)
        {
            BaseColumnInfo colInfo = getWrappedIdCol(parentTable.getUserSchema(), "demographicsOutcomes", parentTable, "outcomes");
            colInfo.setLabel("Outcomes");
            parentTable.addColumn(colInfo);
        }
    }

    private void appendPvlColumns(DatasetTable ds, String subjectColName, String dateColName)
    {
        final String name = "viralLoad";
        if (ds.getColumn(name) != null)
        {
            return;
        }

        Dataset vl = ds.getDataset().getStudy().getDatasetByName("viralloads");
        if (vl == null)
        {
            return;
        }

        if (ds instanceof AbstractTableInfo ti)
        {
            ColumnInfo subjectCol = ti.getColumn(subjectColName);
            ColumnInfo dateCol = ti.getColumn(dateColName);

            final String tableName = vl.getDomain().getStorageTableName();
            SQLFragment sql = new SQLFragment("(SELECT CASE WHEN count(t.result) = 1 THEN max(t.result) ELSE null END as expr FROM studydataset." + tableName + " t WHERE t.participantid = " + ExprColumn.STR_TABLE_ALIAS + ".participantid AND CAST(t.date AS DATE) = CAST(" + ExprColumn.STR_TABLE_ALIAS + ".date AS DATE) AND t.sampletype = 'Plasma' AND t.target = 'SIV')");
            ExprColumn newCol = new ExprColumn(ti, name, sql, JdbcType.DOUBLE, subjectCol, dateCol);
            newCol.setDescription("Displays the viral load from this timepoint, if present");
            newCol.setLabel("SIV PVL (copies/mL)");
            newCol.setDisplayColumnFactory(ResultsOORDisplayColumn::new);
            ti.addColumn(newCol);

            String nameOOR = name + "OORIndicator";
            SQLFragment sqlOOR = new SQLFragment("(SELECT CASE WHEN count(t.result) = 1 THEN max(t.resultOORIndicator) ELSE null END as expr FROM studydataset." + tableName + " t WHERE t.participantid = " + ExprColumn.STR_TABLE_ALIAS + ".participantid AND CAST(t.date AS DATE) = CAST(" + ExprColumn.STR_TABLE_ALIAS + ".date AS DATE) AND t.sampletype = 'Plasma' AND t.target = 'SIV')");
            ExprColumn newColOOR = new ExprColumn(ti, nameOOR, sqlOOR, JdbcType.VARCHAR, subjectCol, dateCol);
            newColOOR.setDescription("For the corresponding PVL, this indicates if the value is out-of-range");
            newColOOR.setLabel("SIV PVL OOR Indicator");
            newColOOR.setHidden(true);

            ti.addColumn(newColOOR);
        }
    }

    private void appendSivChallengeColumns(AbstractTableInfo targetTable, String subjectColName, String dateColName)
    {
        String name = "timePostSivChallenge";
        if (targetTable.getColumn(name, false) != null)
            return;

        final ColumnInfo pkCol = getPkCol(targetTable);
        if (pkCol == null)
            return;

        final ColumnInfo idCol = targetTable.getColumn(subjectColName);
        if (idCol == null)
            return;

        final ColumnInfo dateCol = targetTable.getColumn(dateColName);
        if (dateCol == null)
            return;

        final String targetSchemaName = targetTable.getUserSchema().getName();
        final Container targetSchemaContainer = targetTable.getUserSchema().getContainer();
        final User u = targetTable.getUserSchema().getUser();
        final String schemaName = targetTable.getPublicSchemaName();
        final String queryName = targetTable.getName();

        WrappedColumn col = new WrappedColumn(pkCol, name);
        col.setLabel("SIV Challenge");
        col.setReadOnly(true);
        col.setIsUnselectable(true);
        col.setUserEditable(false);
        col.setFk(new LookupForeignKey(){
            @Override
            public TableInfo getLookupTableInfo()
            {
                String name = queryName + "_sivChallenge";
                UserSchema targetSchema = targetTable.getUserSchema().getDefaultSchema().getUserSchema(targetSchemaName);
                QueryDefinition qd = QueryService.get().createQueryDef(u, targetSchemaContainer, targetSchema, name);
                qd.setSql("SELECT\n" +
                        "max(ad.date) as infectionDate,\n" +
                        // NOTE: CAST() is used to ensure whole numbers
                        "CONVERT(TIMESTAMPDIFF('SQL_TSI_DAY', CAST(max(ad.date) AS DATE), CAST(c." + dateColName + " AS DATE)), INTEGER) as daysPostInfection,\n" +
                        "CONVERT(age_in_months(CAST(max(ad.date) AS DATE), CAST(c." + dateColName + " AS DATE)), FLOAT) as monthsPostInfection,\n" +
                        "c." + pkCol.getFieldKey().toString() + "\n" +
                        "FROM \"" + schemaName + "\".\"" + queryName + "\" c " +
                        "JOIN studies.subjectAnchorDates ad ON (ad.subjectId = c." + idCol.getFieldKey().toSQLString() + ")\n" +
                        "WHERE ad.eventLabel = 'SIV Infection'\n" +
                        "GROUP BY c.date, c." + pkCol.getFieldKey().toString() + "\n" +
                        "HAVING count(*) = 1"
                );
                qd.setIsTemporary(true);

                List<QueryException> errors = new ArrayList<>();
                TableInfo ti = qd.getTable(errors, true);
                if (!errors.isEmpty())
                {
                    _log.warn("Error creating sivChallenge lookup table for: " + schemaName + "." + queryName + " in container: " + targetSchema.getContainer().getPath());
                    for (QueryException e : errors)
                    {
                        _log.warn(e.getMessage(), e);
                    }
                }

                if (ti != null)
                {
                    ((BaseColumnInfo)ti.getColumn(pkCol.getName())).setHidden(true);
                    ((BaseColumnInfo)ti.getColumn(pkCol.getName())).setKeyField(true);

                    ((BaseColumnInfo)ti.getColumn("infectionDate")).setLabel("Infection Date");
                    ((BaseColumnInfo)ti.getColumn("daysPostInfection")).setLabel("Days Post-Infection");
                    ((BaseColumnInfo)ti.getColumn("monthsPostInfection")).setLabel("Months Post-Infection");
                }

                return ti;
            }
        });

        targetTable.addColumn(col);
    }

    private void appendArtColumns(DatasetTable ds, String subjectColName, String dateColName)
    {
        String name = "artInformation";
        if (ds.getColumn(name) != null)
            return;

        final ColumnInfo pkCol = getPkCol(ds);
        if (pkCol == null)
            return;

        final ColumnInfo idCol = ds.getColumn(subjectColName);
        if (idCol == null)
            return;

        final ColumnInfo dateCol = ds.getColumn(dateColName);
        if (dateCol == null)
            return;

        Dataset treatments = ds.getDataset().getStudy().getDatasetByName("treatments");
        if (treatments == null)
        {
            return;
        }

        final String targetSchemaName = ds.getUserSchema().getName();
        final Container targetSchemaContainer = ds.getUserSchema().getContainer();
        final User u = ds.getUserSchema().getUser();
        final String schemaName = ds.getPublicSchemaName();
        final String queryName = ds.getName();

        WrappedColumn col = new WrappedColumn(pkCol, name);
        col.setLabel("ART Information");
        col.setReadOnly(true);
        col.setIsUnselectable(true);
        col.setUserEditable(false);
        col.setFk(new LookupForeignKey(){
            @Override
            public TableInfo getLookupTableInfo()
            {
                String name = queryName + "_artData";
                UserSchema targetSchema = ds.getUserSchema().getDefaultSchema().getUserSchema(targetSchemaName);
                QueryDefinition qd = QueryService.get().createQueryDef(u, targetSchemaContainer, targetSchema, name);
                qd.setSql("SELECT\n" +
                        "max(tr.date) as artInitiation,\n" +
                        "CONVERT(TIMESTAMPDIFF('SQL_TSI_DAY', CAST(max(tr.date) AS DATE), CAST(c." + dateColName + " AS DATE)), INTEGER) as daysPostArtInitiation,\n" +
                        "CONVERT(age_in_months(CAST(max(tr.date) AS DATE), CAST(c." + dateColName + " AS DATE)), FLOAT) as monthsPostArtInitiation,\n" +
                        "max(tr.enddate) as artRelease,\n" +
                        "CONVERT(TIMESTAMPDIFF('SQL_TSI_DAY', CAST(max(tr.enddate) AS DATE), CAST(c." + dateColName + " AS DATE)), INTEGER) as daysPostArtRelease,\n" +
                        "CONVERT(age_in_months(CAST(max(tr.enddate) AS DATE), CAST(c." + dateColName + " AS DATE)), FLOAT) as monthsPostArtRelease,\n" +
                        "CAST(CASE WHEN max(tr.date) IS NULL THEN NULL ELSE 'Y' END as VARCHAR) as onArt,\n" +
                        "GROUP_CONCAT(DISTINCT tr.treatment) AS artTreatment,\n" +
                        "c." + pkCol.getFieldKey().toString() + "\n" +
                        "FROM \"" + schemaName + "\".\"" + queryName + "\" c " +
                        "JOIN study.treatments tr ON (tr.category = 'ART' AND CAST(tr.date AS DATE) <= CAST(c." + dateCol.getFieldKey().toString() + " AS DATE) AND COALESCE(tr.enddate, now()) >= CAST(c." + dateCol.getFieldKey().toString() + " AS DATE) AND tr.Id = c." + idCol.getFieldKey().toSQLString() + ")\n" +
                        "GROUP BY c.date, c." + pkCol.getFieldKey().toString() + "\n" +
                        "HAVING COUNT(*) = 1"
                );
                qd.setIsTemporary(true);

                List<QueryException> errors = new ArrayList<>();
                TableInfo ti = qd.getTable(errors, true);
                if (!errors.isEmpty())
                {
                    _log.warn("Error creating artData lookup table for: " + schemaName + "." + queryName + " in container: " + targetSchema.getContainer().getPath());
                    for (QueryException e : errors)
                    {
                        _log.warn(e.getMessage(), e);
                    }
                }

                if (ti != null)
                {
                    ((BaseColumnInfo)ti.getColumn(pkCol.getName())).setHidden(true);
                    ((BaseColumnInfo)ti.getColumn(pkCol.getName())).setKeyField(true);

                    ((BaseColumnInfo)ti.getColumn("artInitiation")).setLabel("ART Initiation");
                    ((BaseColumnInfo)ti.getColumn("artRelease")).setLabel("ART Release");

                    ((BaseColumnInfo)ti.getColumn("daysPostArtInitiation")).setLabel("Days Post-ART Initiation");
                    ((BaseColumnInfo)ti.getColumn("monthsPostArtInitiation")).setLabel("Months Post-ART Initiation");

                    ((BaseColumnInfo)ti.getColumn("daysPostArtRelease")).setLabel("Days Post-ART Release");
                    ((BaseColumnInfo)ti.getColumn("monthsPostArtRelease")).setLabel("Months Post-ART Release");

                    ((BaseColumnInfo)ti.getColumn("artTreatment")).setLabel("ART Treatment(s)");
                    ((BaseColumnInfo)ti.getColumn("onArt")).setLabel("Overlaps ART?");
                }

                return ti;
            }
        });

        if (ds instanceof AbstractTableInfo ati)
        {
            ati.addColumn(col);
        }
    }


    // TODO: was on ART or not??

    private BaseColumnInfo getWrappedIdCol(UserSchema targetQueryUserSchema, String targetQueryName, AbstractTableInfo demographicsTable, String colName)
    {
        WrappedColumn col = new WrappedColumn(demographicsTable.getColumn(ID_COL), colName);
        col.setReadOnly(true);
        col.setIsUnselectable(true);
        col.setUserEditable(false);
        col.setFk(new QueryForeignKey(demographicsTable.getUserSchema(), null, targetQueryUserSchema, null, targetQueryName, ID_COL, ID_COL));

        return col;
    }

    private void customizeViralLoads(AbstractTableInfo ati)
    {
        ati.addTriggerFactory(new ViralLoadsTriggerFactory());
    }
}
