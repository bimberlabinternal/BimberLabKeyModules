package org.labkey.tcrdb;

import org.labkey.api.data.Container;
import org.labkey.api.laboratory.AbstractImportingNavItem;
import org.labkey.api.laboratory.DataProvider;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

public class TCRdbImportNavItem extends AbstractImportingNavItem
{
    public static final String NAME = "TCR Sorts/Stims";

    public TCRdbImportNavItem(DataProvider provider, String label, LaboratoryService.NavItemCategory itemType, String reportCategory)
    {
        super(provider, NAME, label, itemType, (reportCategory == null ? "TCRdb" : reportCategory));
    }

    @Override
    public ActionURL getImportUrl(Container c, User u)
    {
        return DetailsURL.fromString("tcrdb/stimDashboard.view").getActionURL();
    }

    @Override
    public ActionURL getSearchUrl(Container c, User u)
    {
        return null;
    }

    @Override
    public ActionURL getBrowseUrl(Container c, User u)
    {
        return DetailsURL.fromString("tcrdb/stimDashboard.view").getActionURL();
    }

    @Override
    public boolean isImportIntoWorkbooks(Container c, User u)
    {
        return true;
    }

    @Override
    public boolean getDefaultVisibility(Container c, User u)
    {
        return getTargetContainer(c).getActiveModules().contains(ModuleLoader.getInstance().getModule(TCRdbModule.NAME));
    }
}
