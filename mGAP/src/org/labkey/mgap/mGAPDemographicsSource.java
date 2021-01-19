package org.labkey.mgap;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.jbrowse.DemographicsSource;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class mGAPDemographicsSource implements DemographicsSource
{

    @Override
    public Map<String, Map<String, Object>> resolveSubjects(List<String> subjects, Container c, User u)
    {
        Map<String, Map<String, Object>> ret = new HashMap<>();

        //determine data types
        Map<String, Set<String>> dataTypeMap = new HashMap<>();
        TableInfo sequence = QueryService.get().getUserSchema(u, c, mGAPSchema.NAME).getTable(mGAPSchema.TABLE_SEQUENCE_DATASETS, null);
        new TableSelector(sequence, PageFlowUtil.set("mgapId", "sequenceType"), new SimpleFilter(FieldKey.fromString("mgapId"), subjects, CompareType.IN), null).forEachResults(rs -> {
            Set<String> types = dataTypeMap.getOrDefault(rs.getString(FieldKey.fromString("mgapId")), new HashSet<>());
            String type = rs.getString(FieldKey.fromString("sequenceType"));
            switch (type)
            {
                case "Whole Genome: Deep Coverage":
                    type = "WGS";
                    break;
                case "Whole Exome":
                    type = "WXS";
                    break;
            }

            types.add(type);

            dataTypeMap.put(rs.getString(FieldKey.fromString("mgapId")), types);
        });

        TableInfo ti = QueryService.get().getUserSchema(u, c, mGAPSchema.NAME).getTable(mGAPSchema.TABLE_DEMOGRAPHICS, null);
        Set<String> fields = new LinkedHashSet<>(getFields().keySet());
        fields.add("subjectname");
        new TableSelector(ti, fields, new SimpleFilter(FieldKey.fromString("subjectname"), subjects, CompareType.IN), null).forEachResults(rs -> {
            Map<String, Object> map = new CaseInsensitiveHashMap<>();
            String subject = rs.getString(FieldKey.fromString("subjectname"));
            for (String field : fields)
            {
                if ("datatypes".equalsIgnoreCase(field))
                {
                    map.put("datatypes", (dataTypeMap.containsKey(subject) ? StringUtils.join(dataTypeMap.get(subject), ",") : null));
                }
                else
                {
                    map.put(field, rs.getObject(FieldKey.fromString(field)));
                }
            }

            ret.put(subject, map);
        });

        return ret;
    }

    @Override
    public LinkedHashMap<String, String> getFields()
    {
        LinkedHashMap<String, String> ret = new LinkedHashMap<>();
        ret.put("gender", "Sex");
        ret.put("species", "Species");
        ret.put("center", "Center");
        ret.put("geographic_origin", "Geographic Origin");
        ret.put("status", "Status");
        ret.put("datatypes", "Datatypes");

        return ret;
    }

    @Override
    public boolean isAvailable(Container c, User u)
    {
        return c.getActiveModules().contains(ModuleLoader.getInstance().getModule(mGAPModule.class));
    }
}
