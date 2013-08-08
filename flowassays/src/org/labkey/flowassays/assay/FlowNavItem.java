package org.labkey.flowassays.assay;

import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.flow.api.FlowService;
import org.labkey.api.laboratory.AbstractQueryNavItem;
import org.labkey.api.laboratory.DataProvider;
import org.labkey.api.laboratory.LaboratoryUrls;
import org.labkey.api.laboratory.assay.AssayDataProvider;
import org.labkey.api.laboratory.assay.AssayNavItem;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssaySchema;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.flowassays.FlowAssaysManager;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 4/14/13
 * Time: 2:16 PM
 */
public class FlowNavItem extends AbstractQueryNavItem
{
    public static final String NAME = "Flow";
    private DataProvider _dp;
    private ExpProtocol _protocol;
    private String _label;
    private String _category;

    public FlowNavItem(AssayDataProvider dp, ExpProtocol protocol)
    {
        _dp = dp;
        _protocol = protocol;
        _label = NAME;
        _category = NAME;
    }

    public DataProvider getDataProvider()
    {
        return _dp;
    }

    public String getName()
    {
        return NAME;
    }

    public String getLabel()
    {
        return _label;
    }

    public boolean isImportIntoWorkbooks(Container c, User u)
    {
        return true;
    }

    public boolean getDefaultVisibility(Container c, User u)
    {
        return c.getActiveModules().contains(ModuleLoader.getInstance().getModule(NAME));
    }

    public String getCategory()
    {
        return _category;
    }

    public ActionURL getImportUrl(Container c, User u)
    {
        return PageFlowUtil.urlProvider(PipelineUrls.class).urlBrowse(c);
    }

    public ActionURL getSearchUrl(Container c, User u)
    {
        return PageFlowUtil.urlProvider(LaboratoryUrls.class).getSearchUrl(c, FlowAssaysManager.FLOW_SCHEMA_NAME, "FCSAnalyses");
    }

    public ActionURL getBrowseUrl(Container c, User u)
    {
        ActionURL url = QueryService.get().urlFor(u, c, QueryAction.executeQuery, FlowAssaysManager.FLOW_SCHEMA_NAME, "FCSAnalyses");
        return appendDefaultView(c, url, "query");
    }
}
