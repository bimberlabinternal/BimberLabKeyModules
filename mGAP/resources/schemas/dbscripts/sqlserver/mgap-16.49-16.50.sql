CREATE TABLE mGAP.pedigreeOverrides (
  rowid int identity(1,1),
  subjectId varchar(100),
  relationship varchar(100),
  parentId varchar(100),
  comment varchar(4000),

  container entityid,
  created datetime,
  createdby userid,
  modified datetime,
  modifiedby userid,

  CONSTRAINT PK_pedigreeOverrides PRIMARY KEY (rowid)
);