package org.labkey.mcc.ehr;

import org.labkey.api.data.Container;
import org.labkey.api.data.Results;
import org.labkey.api.ehr.history.AbstractDataSource;
import org.labkey.api.module.Module;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.Formats;
import org.labkey.api.util.PageFlowUtil;

import java.sql.SQLException;
import java.util.Set;

/**
 * User: bimber
 * Date: 2/17/13
 * Time: 4:52 PM
 */
public class MccWeightDataSource extends AbstractDataSource
{
    public MccWeightDataSource(Module module)
    {
        super("study", "Weight", "Weight", "Weights", module);
        setShowTime(true);
    }

    @Override
    protected Set<String> getColumnNames()
    {
        return PageFlowUtil.set("Id", "date", "weightGrams", "objectid");
    }

    @Override
    protected String getHtml(Container c, Results rs, boolean redacted) throws SQLException
    {
        if (rs.hasColumn(FieldKey.fromString("weightGrams")) && rs.getObject("weightGrams") != null)
        {
            double serverWeight = rs.getDouble("weightGrams");
            return "Weight: " + Formats.f2.format(serverWeight) +" g";
        }

        return null;
    }
}
