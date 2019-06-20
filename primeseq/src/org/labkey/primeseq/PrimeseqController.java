/*
 * Copyright (c) 2019 LabKey Corporation
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

package org.labkey.primeseq;

import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.ActionURL;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrimeseqController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(PrimeseqController.class);
    public static final String NAME = "primeseq";

    public PrimeseqController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(ReadPermission.class)
    public class GetNavItemsAction extends ReadOnlyApiAction<Object>
    {
        public ApiResponse execute(Object form, BindException errors)
        {
            Map<String, Object> resultProperties = new HashMap<>();

            resultProperties.put("collaborations", getSection("/Public/Collaborations"));
            resultProperties.put("internal", getSection("/Internal"));
            resultProperties.put("labs", getSection("/Labs"));

            //for now, public is hard coded
            List<JSONObject> publicJson = new ArrayList<>();
            Container publicContainer = ContainerManager.getForPath("/Public");
            if (publicContainer != null)
            {
                JSONObject json = new JSONObject();
                json.put("name", "Front Page");
                json.put("path", ContainerManager.getHomeContainer().getPath());
                json.put("url", ContainerManager.getHomeContainer().getStartURL(getUser()).toString());
                json.put("canRead", ContainerManager.getHomeContainer().hasPermission(getUser(), ReadPermission.class));
                publicJson.add(json);

                json = new JSONObject();
                json.put("name", "Overview / Tutorials");
                json.put("path", publicContainer.getPath());
                json.put("url", publicContainer.getStartURL(getUser()).toString());
                json.put("canRead", publicContainer.hasPermission(getUser(), ReadPermission.class));
                publicJson.add(json);

                json = new JSONObject();
                Container publicBlast = ContainerManager.getForPath("/Public/PublicBLAST");
                if (publicBlast != null)
                {
                    json.put("name", "BLAST");
                    json.put("path", publicBlast.getPath());
                    json.put("url", new ActionURL("blast", "blast", publicBlast).toString());
                    json.put("canRead", publicBlast.hasPermission(getUser(), ReadPermission.class));
                    publicJson.add(json);
                }

                json = new JSONObject();
                json.put("name", "Genome Browser");
                json.put("path", publicContainer.getPath());
                json.put("url", new ActionURL("wiki", "page", publicContainer).toString() + "name=Genome Browser Instructions");
                json.put("canRead", publicContainer.hasPermission(getUser(), ReadPermission.class));
                publicJson.add(json);

            }

            resultProperties.put("public", publicJson);
            resultProperties.put("success", true);

            return new ApiSimpleResponse(resultProperties);
        }

        private List<JSONObject> getSection(String path)
        {
            List<JSONObject> ret = new ArrayList<>();
            Container mainContainer = ContainerManager.getForPath(path);
            if (mainContainer != null)
            {
                for (Container c : mainContainer.getChildren())
                {
                    //NOTE: unlike EHR, omit children if the current user cannot read them
                    if (!c.hasPermission(getUser(), ReadPermission.class))
                    {
                        continue;
                    }

                    JSONObject json = new JSONObject();
                    json.put("name", c.getName());
                    json.put("title", c.getTitle());
                    json.put("path", c.getPath());
                    json.put("url", c.getStartURL(getUser()));
                    json.put("canRead", c.hasPermission(getUser(), ReadPermission.class));
                    ret.add(json);
                }
            }

            return ret;
        }
    }
}