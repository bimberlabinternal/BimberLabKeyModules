/*
 * Copyright (c) 2012 LabKey Corporation
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

package org.labkey.genotypeassays;

import org.apache.commons.text.StringEscapeUtils;
import org.json.JSONArray;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ContainerType;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.Pair;
import org.springframework.validation.BindException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GenotypeAssaysController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(GenotypeAssaysController.class);

    public GenotypeAssaysController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(UpdatePermission.class)
    public static class CacheAnalysesAction extends MutatingApiAction<CacheAnalysesForm>
    {
        @Override
        public ApiResponse execute(CacheAnalysesForm form, BindException errors)
        {
            Map<String, Object> resultProperties = new HashMap<>();

            //first verify permission to delete
            if (form.getAlleleNames() != null)
            {
                try
                {
                    ExpProtocol protocol = ExperimentService.get().getExpProtocol(form.getProtocolId());
                    if (protocol == null)
                    {
                        errors.reject(ERROR_MSG, "Unknown protocol: " + form.getProtocolId());
                        return null;
                    }

                    if (!protocol.getContainer().getContainerFor(ContainerType.DataType.tabParent).equals(getContainer().getContainerFor(ContainerType.DataType.tabParent)))
                    {
                        errors.reject(ERROR_MSG, "Protocol is from the wrong container: " + form.getProtocolId());
                        logger.error("CacheAnalysesAction targeted a protocol from the wrong container: {}, from {}, in the container: {}", form.getProtocolId(), protocol.getContainer().getPath(), getContainer().getPath());
                        return null;
                    }

                    String[] alleleNames = Arrays.stream(form.getAlleleNames()).map(StringEscapeUtils::unescapeHtml4).toArray(String[]::new);
                    Pair<List<Long>, List<Long>> ret = GenotypeAssaysManager.get().cacheAnalyses(getViewContext(), protocol, alleleNames);
                    resultProperties.put("runsCreated", ret.first);
                    resultProperties.put("runsDeleted", ret.second);
                }
                catch (IllegalArgumentException e)
                {
                    errors.reject(ERROR_MSG, e.getMessage());
                    return null;
                }
            }
            else
            {
                errors.reject(ERROR_MSG, "No alleles provided");
                return null;
            }

            resultProperties.put("success", true);

            return new ApiSimpleResponse(resultProperties);
        }
    }

    public static class CacheAnalysesForm
    {
        private String[] _alleleNames;
        private String _json;
        private int _protocolId;

        public String[] getAlleleNames()
        {
            return _alleleNames;
        }

        public void setAlleleNames(String[] alleleNames)
        {
            _alleleNames = alleleNames;
        }

        public int getProtocolId()
        {
            return _protocolId;
        }

        public void setProtocolId(int protocolId)
        {
            _protocolId = protocolId;
        }

        public String getJson()
        {
            return _json;
        }

        public void setJson(String json)
        {
            _json = json;
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public static class CacheHaplotypesAction extends MutatingApiAction<CacheAnalysesForm>
    {
        @Override
        public ApiResponse execute(CacheAnalysesForm form, BindException errors)
        {
            Map<String, Object> resultProperties = new HashMap<>();

            //first verify permission to delete
            if (form.getJson() != null)
            {
                try
                {
                    ExpProtocol protocol = ExperimentService.get().getExpProtocol(form.getProtocolId());
                    if (protocol == null)
                    {
                        errors.reject(ERROR_MSG, "Unknown protocol: " + form.getProtocolId());
                        return null;
                    }

                    if (!protocol.getContainer().getContainerFor(ContainerType.DataType.tabParent).equals(getContainer().getContainerFor(ContainerType.DataType.tabParent)))
                    {
                        errors.reject(ERROR_MSG, "Protocol is from the wrong container: " + form.getProtocolId());
                        logger.error("CacheHaplotypesAction targeted a protocol from the wrong container: {}, from {}, in the container: {}", form.getProtocolId(), protocol.getContainer().getPath(), getContainer().getPath());
                        return null;
                    }

                    Pair<List<Long>, List<Long>> ret = GenotypeAssaysManager.get().cacheHaplotypes(getViewContext(), protocol, new JSONArray(form.getJson()));
                    resultProperties.put("runsCreated", ret.first);
                    resultProperties.put("runsDeleted", ret.second);
                }
                catch (IllegalArgumentException e)
                {
                    errors.reject(ERROR_MSG, e.getMessage());
                    return null;
                }
            }
            else
            {
                errors.reject(ERROR_MSG, "No data provided");
                return null;
            }

            resultProperties.put("success", true);

            return new ApiSimpleResponse(resultProperties);
        }
    }
}