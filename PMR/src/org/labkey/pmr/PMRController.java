/*
 * Copyright (c) 2023 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.pmr;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.ehr.EHRService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.studies.StudiesService;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.HtmlView;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

public class PMRController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(PMRController.class);
    public static final String NAME = "pmr";

    public PMRController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(AdminPermission.class)
    public static class ImportStudyAction extends ConfirmAction<Object>
    {
        @Override
        public ModelAndView getConfirmView(Object o, BindException errors) throws Exception
        {
            setTitle("Import mGAP Study");

            return new HtmlView(HtmlString.unsafe("This will import the default mGAP study in this folder and set the EHRStudyContainer property to point to this container. Do you want to continue?"));
        }

        @Override
        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            Module ehr = ModuleLoader.getInstance().getModule("ehr");
            ModuleProperty mp = ehr.getModuleProperties().get("EHRStudyContainer");
            mp.saveValue(getUser(), getContainer(), getContainer().getPath());

            StudiesService.get().importFolderDefinition(getContainer(), getUser(), ModuleLoader.getInstance().getModule(PMRModule.NAME), new Path("referenceStudy"));

            EHRService.get().ensureStudyQCStates(getContainer(), getUser(), true);

            return true;
        }

        @Override
        public void validateCommand(Object o, Errors errors)
        {

        }

        @NotNull
        @Override
        public URLHelper getSuccessURL(Object o)
        {
            return PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(getContainer());
        }
    }
}
