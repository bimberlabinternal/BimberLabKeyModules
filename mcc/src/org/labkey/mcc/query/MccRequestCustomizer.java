package org.labkey.mcc.query;

import org.labkey.api.data.Container;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.ldk.table.AbstractTableCustomizer;
import org.labkey.api.ldk.table.CustomPermissionsTable;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.mcc.MccManager;
import org.labkey.mcc.MccSchema;

import java.util.Collections;

public class MccRequestCustomizer extends AbstractTableCustomizer
{
    @Override
    public void customize(TableInfo tableInfo)
    {
        if (tableInfo instanceof CustomPermissionsTable<?> ati)
        {
            customizeRequestScore(ati);
        }
    }

    private void customizeRequestScore(CustomPermissionsTable<?> ti)
    {
        if (ti.getColumn("rabReviewStatus") == null)
        {
            SQLFragment sql = new SQLFragment("(SELECT CONCAT(COALESCE(CAST(sum(CASE WHEN r.review IS NULL THEN 0 ELSE 1 END) as varchar), '0'), ' of ', cast(count(*) as varchar), ' completed') as expr FROM mcc.requestReviews r WHERE r.requestId = " + ExprColumn.STR_TABLE_ALIAS + ".requestId)");
            ExprColumn newCol = new ExprColumn(ti, "rabReviewStatus", sql, JdbcType.VARCHAR, ti.getColumn("requestId"));
            newCol.setSortFieldKeys(Collections.singletonList(FieldKey.fromString("pendingRabReviews")));

            newCol.setLabel("RAB Review Status");
            newCol.setDisplayColumnFactory(colInfo -> {
                return new DataColumn(colInfo)
                {
                    @Override
                    public boolean isFilterable()
                    {
                        return false;
                    }

                    @Override
                    public boolean isSortable()
                    {
                        return false;
                    }
                };
            });

            ti.addColumn(newCol);

            SQLFragment sql2 = new SQLFragment("(SELECT COALESCE(sum(CASE WHEN r.review IS NULL THEN 1 ELSE 0 END), -1) as expr FROM mcc.requestReviews r WHERE r.requestId = " + ExprColumn.STR_TABLE_ALIAS + ".requestId)");
            ExprColumn newCol2 = new ExprColumn(ti, "pendingRabReviews", sql2, JdbcType.INTEGER, ti.getColumn("requestId"));
            newCol2.setLabel("Pending RAB Reviews");
            ti.addColumn(newCol2);
        }

        if (ti.getColumn("transferIds") == null)
        {
            Container dataContainer = MccManager.get().getMCCContainer(ti.getContainer());
            if (dataContainer != null)
            {
                Study s = StudyService.get().getStudy(dataContainer);
                if (s == null)
                {
                    return;
                }

                Dataset d = s.getDatasetByName("departure");
                if (d == null)
                {
                    _log.error("Unable to find study.departure");
                    return;
                }

                TableInfo dti = StorageProvisioner.createTableInfo(d.getDomain());

                SQLFragment sql = new SQLFragment("(SELECT ").
                        append(ti.getSqlDialect().getGroupConcat(new SQLFragment("d." + dti.getColumn("participantid").getSelectName()), true, true, new SQLFragment("', '"))).
                        append(" as expr FROM ").
                        append(" mcc." + MccSchema.TABLE_ANIMAL_REQUESTS + " ar JOIN studydataset.").append(dti.getName()).
                        append(" d ON (d.mccRequestId = ar.rowId)").
                        append(" WHERE ar.objectid = " + ExprColumn.STR_TABLE_ALIAS + ".requestId)");

                ExprColumn newCol = new ExprColumn(ti, "transferIds", sql, JdbcType.VARCHAR, ti.getColumn("requestId"));
                newCol.setLabel("Animal ID(s)");
                newCol.setWidth("100");
                ti.addColumn(newCol);
            }
        }
    }
}
