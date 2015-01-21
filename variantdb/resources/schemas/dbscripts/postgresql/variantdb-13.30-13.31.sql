/*
 * Copyright (c) 2014 LabKey Corporation
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

CREATE SCHEMA variantdb;

--reference variants
CREATE TABLE variantdb.ReferenceVariants (
  objectid ENTITYID NOT NULL,

  sequenceid int,
  startPosition int,
  endPosition int,
  reference varchar(100),
  allele varchar(100),
  dataSource varchar(100),
  batchId ENTITYID,

  createdBy USERID,
  created TIMESTAMP,
  modifiedBy USERID,
  modified TIMESTAMP,

  constraint PK_ReferenceVariants PRIMARY KEY (objectid)
);

--variant attributes
CREATE TABLE variantdb.VariantAttributes (
  rowid SERIAL,
  variantId ENTITYID,
  attributeId int,

  createdBy USERID,
  created TIMESTAMP,
  modifiedBy USERID,
  modified TIMESTAMP,

  constraint PK_VariantAttributes PRIMARY KEY (rowid)
);

--list of attribute types
CREATE TABLE variantdb.VariantAttributeTypes (
  rowid SERIAL,
  name varchar(100),
  category varchar(100),

  constraint UNIQUE_VariantAttributeTypes UNIQUE (name, category),
  constraint PK_VariantAttributeTypes PRIMARY KEY (rowid)
);

--variant/data mapping
CREATE TABLE variantdb.VariantSampleMapping (
  rowid SERIAL,
  variantId ENTITYID,
  readset int,

  dataId int,
  batchId ENTITYID,

  container ENTITYID,
  createdBy USERID,
  created TIMESTAMP,
  modifiedBy USERID,
  modified TIMESTAMP,

  constraint PK_VariantSampleMapping PRIMARY KEY (rowid)
);

CREATE TABLE variantdb.UploadBatches (
  batchId ENTITYID,
  type varchar(100),
  description varchar(4000),
  source varchar(100),
  build varchar(100),
  dataId int,
  jobId ENTITYID,
  runId int,

  createdBy USERID,
  created TIMESTAMP,
  modifiedBy USERID,
  modified TIMESTAMP,

  constraint PK_UploadBatches PRIMARY KEY (batchId)
);