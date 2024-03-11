package org.labkey.hivrc;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

public class HivrcController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(HivrcController.class);

    public static final String NAME = "hivrc";

    public HivrcController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(AdminPermission.class)
    public static class SetupDefaultsAction extends ConfirmAction<Object>
    {
        @Override
        public ModelAndView getConfirmView(Object o, BindException errors) throws Exception
        {
            setTitle("Set up HIVRC");

            return new HtmlView(HtmlString.unsafe("This will ensure various settings required by HIVRC are configured in this folder. Do you want to continue?"));
        }

        @Override
        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            Module m = ModuleLoader.getInstance().getModule("laboratory");
            ModuleProperty mp = m.getModuleProperties().get("DefaultWorkbookFolderType");
            mp.saveValue(getUser(), getContainer(), "HIVRC Analyses");

            return true;
        }

        @Override
        public void validateCommand(Object o, Errors errors)
        {

        }

        @Override
        public @NotNull URLHelper getSuccessURL(Object o)
        {
            return getContainer().getStartURL(getUser());
        }
    }
}
