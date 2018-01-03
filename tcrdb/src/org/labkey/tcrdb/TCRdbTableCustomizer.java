package org.labkey.tcrdb;

import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.ldk.table.AbstractTableCustomizer;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;

import java.util.Arrays;
import java.util.List;

public class TCRdbTableCustomizer extends AbstractTableCustomizer
{
    @Override
    public void customize(TableInfo table)
    {
        if (table instanceof AbstractTableInfo)
        {
            AbstractTableInfo ti = (AbstractTableInfo) table;
            if (matches(ti, "sequenceanalysis", "sequence_analyses"))
            {
                addAssayFieldsToAnalyses(ti);
            }
            else if (matches(ti, "sequenceanalysis", "sequence_readsets"))
            {
                addAssayFieldsToReadsets(ti);
            }
            else if (matches(ti, "tcrdb", "stims"))
            {
                customizeStims(ti);
            }
            else if (matches(ti, "tcrdb", "sorts"))
            {
                customizeSorts(ti);
            }
        }
    }

    private void customizeSorts(AbstractTableInfo ti)
    {
        //TODO:
        // summarize clonotype
        // # loci
        // # reads
        // make a saved view with this info

        String name = "numLibraries";
        if (ti.getColumn(name) == null)
        {
            DetailsURL details = DetailsURL.fromString("/query/executeQuery.view?schemaName=tcrdb&query.queryName=cdnas&query.sortId~eq=${rowid}", (ti.getUserSchema().getContainer().isWorkbook() ? ti.getUserSchema().getContainer().getParent() : ti.getUserSchema().getContainer()));

            SQLFragment sql = new SQLFragment("(select count(*) as expr FROM " + TCRdbSchema.NAME + "." + TCRdbSchema.TABLE_CDNAS + " s WHERE s.sortId = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)");
            ExprColumn newCol = new ExprColumn(ti, "numLibraries", sql, JdbcType.INTEGER, ti.getColumn("rowid"));
            newCol.setLabel("# cDNA Libraries");
            newCol.setURL(details);
            ti.addColumn(newCol);
        }
    }

    private void customizeStims(AbstractTableInfo ti)
    {
        String name = "numSorts";
        if (ti.getColumn(name) == null)
        {
            DetailsURL details = DetailsURL.fromString("/query/executeQuery.view?schemaName=tcrdb&query.queryName=sorts&query.stimId~eq=${rowid}", (ti.getUserSchema().getContainer().isWorkbook() ? ti.getUserSchema().getContainer().getParent() : ti.getUserSchema().getContainer()));

            SQLFragment sql = new SQLFragment("(select count(*) as expr FROM " + TCRdbSchema.NAME + "." + TCRdbSchema.TABLE_SORTS + " s WHERE s.stimId = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)");
            ExprColumn newCol = new ExprColumn(ti, "numSorts", sql, JdbcType.INTEGER, ti.getColumn("rowid"));
            newCol.setLabel("# Sorts");
            newCol.setURL(details);
            ti.addColumn(newCol);
        }
    }

    private void addAssayFieldsToReadsets(AbstractTableInfo ti)
    {
        addAssayFieldsToTable(ti, "analysisId/readset", "LEFT JOIN sequenceanalysis.sequence_analyses a2 ON (a.analysisId = a2.rowId) WHERE a2.readset = " + ExprColumn.STR_TABLE_ALIAS + ".rowid");
    }

    private void addAssayFieldsToAnalyses(AbstractTableInfo ti)
    {
        addAssayFieldsToTable(ti, "analysisId", "WHERE a.analysisId = " + ExprColumn.STR_TABLE_ALIAS + ".rowid");
    }

    private void addAssayFieldsToTable(AbstractTableInfo ti, String urlField, String whereClause)
    {
        if (ti.getColumn("numTcrResults") == null)
        {
            AssayProvider ap = AssayService.get().getProvider("TCRdb");
            if (ap == null)
            {
                return;
            }

            List<ExpProtocol> protocols = AssayService.get().getAssayProtocols(ti.getUserSchema().getContainer(), ap);
            if (protocols.size() != 1)
            {
                return;
            }

            AssayProtocolSchema schema = ap.createProtocolSchema(ti.getUserSchema().getUser(), ti.getUserSchema().getContainer(), protocols.get(0), null);
            TableInfo data = schema.getTable("data");

            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("locus"), "None", CompareType.NEQ_OR_NULL);
            filter.addCondition(FieldKey.fromString("disabled"), true, CompareType.NEQ_OR_NULL);

            SQLFragment selectSql = QueryService.get().getSelectSQL(data, Arrays.asList(data.getColumn("analysisId"), data.getColumn("CDR3"), data.getColumn("locus")), filter, null, Table.ALL_ROWS, Table.NO_OFFSET, false);
            DetailsURL details = DetailsURL.fromString("/query/executeQuery.view?schemaName=assay." + ap.getName().replaceAll(" ", "") + "." + protocols.get(0).getName() + "&query.queryName=data&query." + urlField + "~eq=${rowid}", (ti.getUserSchema().getContainer().isWorkbook() ? ti.getUserSchema().getContainer().getParent() : ti.getUserSchema().getContainer()));

            SQLFragment sql = new SQLFragment("(select count(*) as expr FROM (").append(selectSql).append(") a " + whereClause + ")");
            ExprColumn newCol = new ExprColumn(ti, "numTcrResults", sql, JdbcType.INTEGER, ti.getColumn("rowid"));
            newCol.setLabel("# TCR Results");
            newCol.setURL(details);
            ti.addColumn(newCol);

            SQLFragment sql2 = new SQLFragment("(select count(distinct CDR3) as expr FROM (").append(selectSql).append(") a " + whereClause + ")");
            ExprColumn newCol2 = new ExprColumn(ti, "numCDR3s", sql2, JdbcType.INTEGER, ti.getColumn("rowid"));
            newCol2.setLabel("# Distinct CDR3s");
            newCol2.setURL(details);
            ti.addColumn(newCol2);

            SQLFragment sql3 = new SQLFragment("(select ").append(ti.getSqlDialect().getGroupConcat(new SQLFragment("locus"), true, true)).append(" FROM (").append(selectSql).append(") a " + whereClause + ")");
            ExprColumn newCol3 = new ExprColumn(ti, "distinctLoci", sql3, JdbcType.VARCHAR, ti.getColumn("rowid"));
            newCol3.setLabel("Distinct Loci");
            newCol3.setURL(details);
            ti.addColumn(newCol3);

            SQLFragment sql4 = new SQLFragment("(select ").append(ti.getSqlDialect().getGroupConcat(new SQLFragment("CDR3"), true, true, getChr(ti) + "(10)")).append(" FROM (").append(selectSql).append(") a " + whereClause + " )");
            ExprColumn newCol4 = new ExprColumn(ti, "distinctCDR3s", sql4, JdbcType.VARCHAR, ti.getColumn("rowid"));
            newCol4.setLabel("Distinct CDR3s");
            newCol4.setURL(details);
            ti.addColumn(newCol4);

            TableInfo runs = schema.getTable("runs");
            SQLFragment runSelectSql = QueryService.get().getSelectSQL(runs, Arrays.asList(runs.getColumn("analysisId")), null, null, Table.ALL_ROWS, Table.NO_OFFSET, false);
            DetailsURL runDetails = DetailsURL.fromString("/query/executeQuery.view?schemaName=assay." + ap.getName().replaceAll(" ", "") + "." + protocols.get(0).getName() + "&query.queryName=runs&query." + urlField + "~eq=${rowid}", (ti.getUserSchema().getContainer().isWorkbook() ? ti.getUserSchema().getContainer().getParent() : ti.getUserSchema().getContainer()));

            SQLFragment sql5 = new SQLFragment("(select count(*) as expr FROM (").append(runSelectSql).append(") a " + whereClause + ")");
            ExprColumn newCol5 = new ExprColumn(ti, "numTcrRuns", sql5, JdbcType.INTEGER, ti.getColumn("rowid"));
            newCol5.setLabel("# TCR Runs");
            newCol5.setURL(runDetails);
            ti.addColumn(newCol5);
        }
    }
}
