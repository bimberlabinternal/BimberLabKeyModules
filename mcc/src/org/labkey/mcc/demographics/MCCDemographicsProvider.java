/*
 * Copyright (c) 2013-2019 LabKey Corporation
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
package org.labkey.mcc.demographics;

import org.labkey.api.ehr.demographics.AbstractDemographicsProvider;
import org.labkey.api.module.Module;
import org.labkey.api.query.FieldKey;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * This is designed to augment the built-in BasicDemographicsProvider
 */
public class MCCDemographicsProvider extends AbstractDemographicsProvider
{
    public MCCDemographicsProvider(Module owner)
    {
        super(owner, "study", "Demographics");
    }

    @Override
    public String getName()
    {
        return "MCCDemographics";
    }

    @Override
    protected Collection<FieldKey> getFieldKeys()
    {
        Set<FieldKey> keys = new HashSet<>();

        keys.add(FieldKey.fromString("lsid"));
        keys.add(FieldKey.fromString("Id"));

        keys.add(FieldKey.fromString("colony"));
        keys.add(FieldKey.fromString("source"));
        keys.add(FieldKey.fromString("dam"));
        keys.add(FieldKey.fromString("sire"));
        return keys;
    }
}
