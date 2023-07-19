package org.labkey.mgap.jbrowse;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.jbrowse.GroupsProvider;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.mgap.mGAPModule;
import org.labkey.mgap.mGAPSchema;

import java.util.List;

public class mGAPGroupsProvider implements GroupsProvider
{
    @Override
    public @Nullable List<String> getGroupMembers(String trackId, String groupName, Container c, User u)
    {
        // TODO: new table?
        TableInfo ti = QueryService.get().getUserSchema(u, c, mGAPSchema.NAME).getTable(mGAPSchema.TABLE_RELEASE_TRACK_SUBSETS);

        return new TableSelector(ti, PageFlowUtil.set("subjectId"), new SimpleFilter(FieldKey.fromString("trackName"), groupName).addCondition(FieldKey.fromString("trackId"), trackId), null).getArrayList(String.class);
    }

    @Override
    public boolean isAvailable(Container c, User u)
    {
        return c.getActiveModules().contains(ModuleLoader.getInstance().getModule(mGAPModule.class));
    }
}
