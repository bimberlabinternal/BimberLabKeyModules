package org.labkey.hivrc;

import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.hivrc.query.AnalysisModel;

public class HivrcManager
{
    private static final HivrcManager _instance = new HivrcManager();

    private HivrcManager()
    {

    }

    public static HivrcManager get()
    {
        return _instance;
    }

    public AnalysisModel getAnalysisModel(Container c, boolean createIfNotPresent)
    {
        if (!c.isWorkbook())
        {
            return null;
        }

        TableInfo ti = HivrcSchema.getInstance().getLaboratorySchema().getTable(HivrcSchema.TABLE_WORKBOOKS);
        TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("container"), c.getId()), null);
        AnalysisModel[] arr = ts.getArray(AnalysisModel.class);
        AnalysisModel m =  arr.length == 0 ? null : arr[0];
        if (m == null)
        {
            if (createIfNotPresent)
            {
                m = AnalysisModel.createNew(c);
            }
            else
            {
                return null;
            }
        }

        if (m.getContainer() == null)
        {
            m.setContainer(c.getId());
        }

        TableInfo ti2 = HivrcSchema.getInstance().getLaboratorySchema().getTable(HivrcSchema.TABLE_WORKBOOK_TAGS);
        TableSelector ts2 = new TableSelector(ti2, PageFlowUtil.set("tag"), new SimpleFilter(FieldKey.fromString("container"), c.getId()), null);
        String[] arr2 = ts2.getArray(String.class);
        m.setTags(arr2);

        return m;
    }
}