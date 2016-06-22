/*
 * Copyright (c) 2015 LabKey Corporation
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

package org.labkey.tcrdb;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.SimpleModule;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.tcrdb.pipeline.MiXCRAnalysis;

import java.util.Collection;
import java.util.Collections;

public class TCRdbModule extends SimpleModule
{
    public static final String NAME = "tcrdb";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public double getVersion()
    {
        return 15.33;
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @Override
    protected void init()
    {
        addController(TCRdbController.NAME, TCRdbController.class);
    }

    @Override
    protected void startupAfterSpringConfig(ModuleContext moduleContext)
    {
        super.startupAfterSpringConfig(moduleContext);

        LaboratoryService.get().registerDataProvider(new TCRdbProvider(this));
        SequenceAnalysisService.get().registerDataProvider(new TCRdbProvider(this));

        //register resources
        new PipelineStartup();
    }

    @Override
    @NotNull
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    @Override
    protected void registerSchemas()
    {
        TCRdbUserSchema.register(this);
    }

    public static class PipelineStartup
    {
        private static final Logger _log = Logger.getLogger(PipelineStartup.class);
        private static boolean _hasRegistered = false;

        public PipelineStartup()
        {
            if (_hasRegistered)
            {
                _log.warn("TCRdb resources have already been registered, skipping");
            }
            else
            {
                SequencePipelineService.get().registerPipelineStep(new MiXCRAnalysis.Provider());

                _hasRegistered = true;
            }
        }
    }
}