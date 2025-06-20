package org.labkey.sivstudies.study;

import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.studies.study.AbstractEventProvider;
import org.labkey.api.study.DatasetTable;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sivstudies.SivStudiesModule;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class SivInfectionEventProvider extends AbstractEventProvider
{
    public SivInfectionEventProvider()
    {
        super("SIV_Infection", "SIV Infection", "This is the official date of SIV infection, used to calculate days-post-infection", ModuleLoader.getInstance().getModule(SivStudiesModule.class));
    }

    @Override
    protected Map<String, Date> inferDatesRaw(Collection<String> subjectList, Container c, User u)
    {
        UserSchema us = QueryService.get().getUserSchema(u, c, "study");
        if (us == null)
        {
            return Collections.emptyMap();
        }

        TableInfo ti = us.getTable("assignment");
        if (ti == null || !ti.hasPermission(u, ReadPermission.class))
        {
            return Collections.emptyMap();
        }

        if (ti instanceof DatasetTable ds)
        {
            Map<String, Date> ret = new HashMap<>();
            final String subjectCol = ds.getDataset().getStudy().getSubjectColumnName();
            new TableSelector(ti, PageFlowUtil.set(subjectCol, "date"), new SimpleFilter(FieldKey.fromString(subjectCol), subjectList, CompareType.IN), null).forEachResults(rs -> {
                ret.put(rs.getString(FieldKey.fromString(subjectCol)), rs.getDate(FieldKey.fromString("date")));
            });

            return ret;
        }
        else
        {
            throw new IllegalStateException("Expected study.assignment to be a DatasetTable");
        }
    }
}
