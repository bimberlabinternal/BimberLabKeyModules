CREATE TABLE variantdb.VariantLiftover (
  rowid SERIAL,
  variantid ENTITYID,

  sequenceid int,
  startPosition int,
  endPosition int,
  reference varchar(100),
  allele varchar(100),
  batchId ENTITYID,

  createdBy USERID,
  created TIMESTAMP,
  modifiedBy USERID,
  modified TIMESTAMP,

  constraint PK_VariantLiftover PRIMARY KEY (rowid)
);