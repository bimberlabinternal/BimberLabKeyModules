package org.labkey.sivstudies.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.table.AbstractTableCustomizer;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.DatasetTable;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;

public class SivStudiesCustomizer extends AbstractTableCustomizer
{
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
        _log.debug("Customizing: {}", ds.getName());

        if (!ds.getDataset().isDemographicData())
        {
            if (ds instanceof AbstractTableInfo ati)
            {
                addAgeAtTimeCol(ati, "date");
            }
            else
            {
                _log.error("Expected DatasetTable to be instanceof AbstractTableInfo");
            }
        }
    }

    private @Nullable String getStudyDemographicsSchemaTableName(Container c, User u)
    {
        Study s = StudyService.get().getStudy(c);
        if (s == null)
        {
            return null;
        }

        Dataset ds = s.getDatasetByLabel("demographics");
        if (ds == null)
        {
            return null;
        }

        return ds.getDomain().getStorageTableName();
    }

    private void addAgeAtTimeCol(AbstractTableInfo ti, String dateColName)
    {
        final String name = "ageAtTime";
        if (ti.getColumn(name, false) != null)
        {
            return;
        }

        final String demographicsTableName = getStudyDemographicsSchemaTableName(ti.getUserSchema().getContainer(), ti.getUserSchema().getUser());
        final FieldKey birthFieldKey = FieldKey.fromString("Id/demographics/birth");

        //new SQLFragment("(SELECT .birth FROM studydatasets." + getStudyDemographicsSchemaTableName())

        // TODO
    }

    private void appendMhcColumns(AbstractTableInfo ti, String dateColName)
    {

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
}
