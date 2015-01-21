DROP TABLE variantdb.ReferenceVariants;

CREATE TABLE variantdb.Variants (
  rowid serial,
  objectid ENTITYID NOT NULL,
  sequenceid int,
  startPosition int,
  endPosition int,
  reference varchar(100),
  allele varchar(100),
  status varchar(100),
  batchId ENTITYID,
  referenceVariantId ENTITYID,
  referenceAlleleId ENTITYID,

  createdBy USERID,
  created TIMESTAMP,
  modifiedBy USERID,
  modified TIMESTAMP,

  constraint PK_Variants PRIMARY KEY (objectid)
);

CREATE TABLE variantdb.ReferenceVariants (
  rowid serial,
  objectid ENTITYID NOT NULL,
  dbSnpAccession VARCHAR(1000),
  organism varchar(100),
  batchId ENTITYID,

  createdBy USERID,
  created TIMESTAMP,
  modifiedBy USERID,
  modified TIMESTAMP,

  constraint PK_ReferenceVariants PRIMARY KEY (objectid)
);

CREATE TABLE variantdb.ReferenceVariantAlleles (
  rowid serial,
  objectid ENTITYID NOT NULL,
  referenceVariantId ENTITYID NOT NULL,
  referencePosition int,
  reference varchar(1000),
  allele varchar(1000),
  status varchar(100),
  batchId ENTITYID,

  createdBy USERID,
  created TIMESTAMP,
  modifiedBy USERID,
  modified TIMESTAMP,

  constraint PK_ReferenceVariantAlleles PRIMARY KEY (objectid)
);