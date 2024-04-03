package org.labkey.mgap;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.laboratory.DemographicsProvider;
import org.labkey.api.module.ModuleLoader;

public class mGAPDemographicsProvider extends DemographicsProvider
{
    public mGAPDemographicsProvider()
    {
        super(ModuleLoader.getInstance().getModule(mGAPModule.class), mGAPSchema.NAME, "combinedPedigree", "subjectname");
    }

    @Nullable
    @Override
    public String getMotherField()
    {
        return "dam";
    }

    @Nullable
    @Override
    public String getFatherField()
    {
        return "sire";
    }

    @Nullable
    @Override
    public String getSexField()
    {
        return "gender";
    }
}
