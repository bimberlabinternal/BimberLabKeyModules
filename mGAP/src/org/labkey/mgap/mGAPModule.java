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

package org.labkey.mgap;

import org.apache.log4j.Logger;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.ldk.ExtendedSimpleModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.mgap.pipeline.PublicReleaseHandler;
import org.labkey.mgap.query.mGAPUserSchema;

public class mGAPModule extends ExtendedSimpleModule
{
    public static final String NAME = "mGAP";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public double getVersion()
    {
        return 16.39;
    }

    @Override
    protected void init()
    {
        addController(mGAPController.NAME, mGAPController.class);
    }

    @Override
    public void doStartupAfterSpringConfig(ModuleContext moduleContext)
    {
        AuditLogService.get().registerAuditType(new mGapAuditTypeProvider());

        new PipelineStartup();
    }

    @Override
    protected void registerSchemas()
    {
        mGAPUserSchema.register(this);
    }

    public static class PipelineStartup
    {
        private static final Logger _log = Logger.getLogger(mGAPModule.PipelineStartup.class);
        private static boolean _hasRegistered = false;

        public PipelineStartup()
        {
            if (_hasRegistered)
            {
                _log.warn("mGAP resources have already been registered, skipping");
            }
            else
            {
                SequenceAnalysisService.get().registerFileHandler(new PublicReleaseHandler());

                _hasRegistered = true;
            }
        }
    }
}