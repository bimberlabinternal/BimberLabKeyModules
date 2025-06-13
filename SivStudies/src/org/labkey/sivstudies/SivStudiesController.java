/*
 * Copyright (c) 2025 LabKey Corporation
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

package org.labkey.sivstudies;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.studies.StudiesService;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.HtmlView;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

public class SivStudiesController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(SivStudiesController.class);
    public static final String NAME = "sivstudies";

    private static final Logger _log = LogHelper.getLogger(SivStudiesController.class, "SivStudiesController messages");

    public SivStudiesController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(AdminPermission.class)
    public static class ImportStudyAction extends ConfirmAction<Object>
    {
        @Override
        public ModelAndView getConfirmView(Object o, BindException errors) throws Exception
        {
            setTitle("Import Study");

            return new HtmlView(HtmlString.unsafe("This will import the default study in this folder, and truncate/load ancillary data. Do you want to continue?"));
        }

        @Override
        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            StudiesService.get().importFolderDefinition(getContainer(), getUser(), ModuleLoader.getInstance().getModule(SivStudiesModule.NAME), new Path("referenceStudy"));

            Module m = ModuleLoader.getInstance().getModule(SivStudiesModule.NAME);
            StudiesService.get().loadTsv(m.getModuleResource("data/lookup_sets.tsv"), "studies", getUser(), getContainer());

            Resource r = m.getModuleResource("data");
            r.list().forEach(tsv -> {
                if ("lookup_sets.tsv".equals(tsv.getName()))
                {
                    return;
                }

                String schemaName = switch (tsv.getName())
                {
                    case "reports.tsv" -> "laboratory";
                    case "species.tsv" -> "laboratory";
                    default -> "studies";
                };

                StudiesService.get().loadTsv(tsv, schemaName, getUser(), getContainer());
            });

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
