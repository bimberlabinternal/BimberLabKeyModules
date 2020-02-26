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

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.dialect.SqlDialect;

public class TCRdbSchema
{
    private static final TCRdbSchema _instance = new TCRdbSchema();
    public static final String NAME = "tcrdb";
    public static final String SEQUENCE_ANALYSIS = "sequenceanalysis";

    public static final String TABLE_LIBRARIES = "mixcr_libraries";
    public static final String TABLE_SORTS = "sorts";
    public static final String TABLE_STIMS = "stims";
    public static final String TABLE_CDNAS = "cdnas";
    public static final String TABLE_CLONES = "clones";
    public static final String TABLE_CITE_SEQ_ANTIBODIES = "citeseq_antibodies";
    public static final String TABLE_CITE_SEQ_PANELS = "citeseq_panels";
    public static final String TABLE_PROCESSING = "plate_processing";

    public static TCRdbSchema getInstance()
    {
        return _instance;
    }

    private TCRdbSchema()
    {

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
