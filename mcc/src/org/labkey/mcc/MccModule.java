/*
 * Copyright (c) 2020 LabKey Corporation
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

package org.labkey.mcc;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.ehr.EHRService;
import org.labkey.api.ldk.ExtendedSimpleModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.view.WebPartFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class MccModule extends ExtendedSimpleModule
{
    public static final String NAME = "MCC";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public @Nullable Double getSchemaVersion()
    {
        return 20.000;
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
        addController(MccController.NAME, MccController.class);
    }

    @Override
    protected void doStartupAfterSpringConfig(ModuleContext moduleContext)
    {
        registerEHRResources();
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
        return Collections.singleton(MccSchema.NAME);
    }

    private void registerEHRResources()
    {
        EHRService.get().registerModule(this);
        //EHRService.get().registerTableCustomizer(this, ONPRC_EHRCustomizer.class);

        //Resource r = getModuleResource("/scripts/mcc/mcc_triggers.js");
        //assert r != null;
        //EHRService.get().registerTriggerScript(this, r);

        //EHRService.get().registerClientDependency(ClientDependency.supplierFromPath("Ext4"), this);
        //EHRService.get().registerClientDependency(ClientDependency.supplierFromPath("onprc_ehr/panel/BloodSummaryPanel.js"), this);

        //EHRService.get().registerReportLink(EHRService.REPORT_LINK_TYPE.housing, "List Single Housed Animals", this, DetailsURL.fromString("/query/executeQuery.view?schemaName=study&query.queryName=demographicsPaired&query.viewName=Single Housed"), "Commonly Used Queries");
        //EHRService.get().registerReportLink(EHRService.REPORT_LINK_TYPE.moreReports, "Clinical Snapshot Printable Report", this, DetailsURL.fromString("/onprc_ehr/SnapshotPrintableReport.view"), "Clinical");

        //EHRService.get().registerDemographicsProvider(new ActiveCasesDemographicsProvider(this));
        //EHRService.get().registerHistoryDataSource(new DefaultSustainedReleaseDatasource(this));
    }
}