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

package org.labkey.elispot_assay;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.ldk.ExtendedSimpleModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.WebPartFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class ELISPOT_AssayModule extends ExtendedSimpleModule
{
    public static final String NAME = "ELISPOT_Assay";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public Double getSchemaVersion()
    {
        return 0.05;
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    @Override
    protected void init()
    {
        addController("elispot_assay", ELISPOT_AssayController.class);
    }

    @Override
    protected void doStartupAfterSpringConfig(ModuleContext moduleContext)
    {
        LaboratoryService.get().registerModule(this);
        LaboratoryService.get().registerDataProvider(new ELISPOT_AssayDataProvider(this));
    }

    @Override
    @NotNull
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return Collections.singleton("elispot_assay");
    }

    @Override
    @NotNull
    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(ELISPOT_AssaySchema.getInstance().getSchema());
    }
}