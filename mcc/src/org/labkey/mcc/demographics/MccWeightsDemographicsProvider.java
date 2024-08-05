package org.labkey.mcc.demographics;

import org.labkey.api.ehr.demographics.WeightsDemographicsProvider;
import org.labkey.api.module.Module;
import org.labkey.api.query.FieldKey;

import java.util.Set;

public class MccWeightsDemographicsProvider extends WeightsDemographicsProvider
{
    public MccWeightsDemographicsProvider(Module owner)
    {
        super(owner);
    }

    @Override
    protected Set<FieldKey> getFieldKeys()
    {
        Set<FieldKey> keys = super.getFieldKeys();
        keys.add(FieldKey.fromString("weightGrams"));

        return keys;
    }
}
