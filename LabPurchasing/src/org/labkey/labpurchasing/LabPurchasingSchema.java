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

package org.labkey.labpurchasing;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.dialect.SqlDialect;

public class LabPurchasingSchema
{
    public static final String TABLE_PURCHASES = "purchases";
    public static final String TABLE_REFERENCE_ITEMS = "referenceItems";
    private static final LabPurchasingSchema _instance = new LabPurchasingSchema();
    public static final String NAME = "labpurchasing";

    public static LabPurchasingSchema getInstance()
    {
        return _instance;
    }

    private LabPurchasingSchema()
    {
        // private constructor to prevent instantiation from
        // outside this class: this singleton should only be
        // accessed via org.labkey.labpurchasing.LabPurchasingSchema.getInstance()
    }

    public DbSchema getSchema()
    {
        return DbSchema.get(NAME, DbSchemaType.Module);
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }
}
