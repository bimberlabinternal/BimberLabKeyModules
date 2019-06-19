CREATE TABLE mGAP.pedigreeOverrides (
  rowid serial,
  subjectId varchar(100),
  relationship varchar(100),
  parentId varchar(100),
  comment varchar(4000),

  container entityid,
  created timestamp,
  createdby userid,
  modified timestamp,
  modifiedby userid,

  CONSTRAINT PK_pedigreeOverrides PRIMARY KEY (rowid)
);