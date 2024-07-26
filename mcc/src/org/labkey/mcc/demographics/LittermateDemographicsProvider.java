package org.labkey.mcc.demographics;

import org.labkey.api.ehr.demographics.AbstractDemographicsProvider;
import org.labkey.api.module.Module;
import org.labkey.api.query.FieldKey;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class LittermateDemographicsProvider extends AbstractDemographicsProvider
{
    public LittermateDemographicsProvider(Module owner)
    {
        super(owner, "study", "demographicsLittermates");
        _supportsQCState = false;
    }

    @Override
    public String getName()
    {
        return "Littermates";
    }

    @Override
    protected Collection<FieldKey> getFieldKeys()
    {
        Set<FieldKey> keys = new HashSet<>();

        keys.add(FieldKey.fromString("Id"));
        keys.add(FieldKey.fromString("litterId"));
        keys.add(FieldKey.fromString("litterMates"));

        return keys;
    }

    @Override
    public boolean requiresRecalc(String schema, String query)
    {
        return ("study".equalsIgnoreCase(schema) && "Demographics".equalsIgnoreCase(query));
    }
}
