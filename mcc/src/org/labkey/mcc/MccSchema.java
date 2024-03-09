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

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.dialect.SqlDialect;

public class MccSchema
{
    private static final MccSchema _instance = new MccSchema();
    public static final String NAME = "mcc";

    public static final String TABLE_USER_REQUESTS = "userRequests";
    public static final String TABLE_ETL_TRANSLATIONS = "etltranslations";
    public static final String TABLE_ANIMAL_MAPPING = "animalMapping";
    public static final String TABLE_ANIMAL_REQUESTS = "animalRequests";
    public static final String TABLE_REQUEST_REVIEWS = "requestReviews";
    public static final String TABLE_REQUEST_SCORE = "requestScores";
    public static final String TABLE_CENSUS = "census";

    public static MccSchema getInstance()
    {
        return _instance;
    }

    private MccSchema()
    {
        // private constructor to prevent instantiation from
        // outside this class: this singleton should only be
        // accessed via org.labkey.mcc.MccSchema.getInstance()
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
