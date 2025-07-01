package org.labkey.sivstudies.study;

import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.studies.study.AbstractEventProvider;
import org.labkey.api.study.DatasetTable;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sivstudies.SivStudiesModule;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ArtInitiationEventProvider extends AbstractEventProvider
{
    public ArtInitiationEventProvider()
    {
        super("ART_Initiation", "ART Initiation", "This is the first date when the subject was assigned to the study, as defined in the study assignment table", ModuleLoader.getInstance().getModule(SivStudiesModule.class));
    }

    @Override
    protected Map<String, Date> inferDatesRaw(Collection<String> subjectList, Container c, User u)
    {
        TableInfo ti = getTable(c, u, "study", "treatments");
        if (ti == null)
        {
            return null;
        }

        if (ti instanceof DatasetTable ds)
        {
            Map<String, Date> ret = new HashMap<>();
            final String subjectCol = ds.getDataset().getStudy().getSubjectColumnName();
            new TableSelector(ti, PageFlowUtil.set(subjectCol, "date"), new SimpleFilter(FieldKey.fromString(subjectCol), subjectList, CompareType.IN), null).forEachResults(rs -> {
                String subjectId = rs.getString(FieldKey.fromString(subjectCol));
                Date date = rs.getDate(FieldKey.fromString("date"));

                if (!ret.containsKey(subjectId) || date.before(ret.get(subjectId)))
                {
                    ret.put(subjectId, date);
                }
            });

            return ret;
        }
        else
        {
            throw new IllegalStateException("Expected study.assignment to be a DatasetTable");
        }
    }
}
