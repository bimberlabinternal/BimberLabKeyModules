CREATE TABLE variantdb.VariantLiftover (
  rowid int identity(1,1),
  variantid ENTITYID,

  sequenceid int,
  startPosition int,
  endPosition int,
  reference varchar(100),
  allele varchar(100),
  batchId ENTITYID,

  createdBy USERID,
  created DATETIME,
  modifiedBy USERID,
  modified DATETIME,

  constraint PK_VariantLiftover PRIMARY KEY (rowid)
);