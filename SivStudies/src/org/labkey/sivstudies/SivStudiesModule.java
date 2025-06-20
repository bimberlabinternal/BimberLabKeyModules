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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.ldk.ExtendedSimpleModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.studies.StudiesService;
import org.labkey.sivstudies.study.ArtInitiationEventProvider;
import org.labkey.sivstudies.study.SivInfectionEventProvider;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class SivStudiesModule extends ExtendedSimpleModule
{
    public static final String NAME = "SivStudies";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public @Nullable Double getSchemaVersion()
    {
        return 25.001;
    }

    @Override
    protected void init()
    {
        addController(SivStudiesController.NAME, SivStudiesController.class);
    }

    @Override
    public void doStartupAfterSpringConfig(ModuleContext moduleContext)
    {
        StudiesService.get().registerEventProvider(new SivInfectionEventProvider());
        StudiesService.get().registerEventProvider(new ArtInitiationEventProvider());
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
        return Collections.singleton(SivStudiesSchema.NAME);
    }
}