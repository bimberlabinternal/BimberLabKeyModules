package org.labkey.mcc.ehr;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.Results;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ehr.history.AbstractDataSource;
import org.labkey.api.ehr.history.HistoryRow;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.mcc.MccModule;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class NoOpClinicalHistorySource extends AbstractDataSource
{
    public NoOpClinicalHistorySource(String categoryText)
    {
        super(null, null, categoryText, ModuleLoader.getInstance().getModule(MccModule.class));

    }

    @Override
    protected TableInfo getTableInfo(Container c, User u)
    {
        throw new UnsupportedOperationException("This should never be called");
    }

    @Override
    @NotNull
    public List<HistoryRow> getRows(Container c, User u, final String subjectId, Date minDate, Date maxDate, boolean redacted)
    {
        return Collections.emptyList();
    }

    @Override
    protected String getHtml(Container c, Results rs, boolean redacted) throws SQLException
    {
        return null;
    }
}
