CREATE TABLE mGAP.animalMapping (
  rowid int IDENTITY(1,1),
  subjectname varchar(100),
  externalAlias varchar(100),

  container entityid,
  created datetime,
  createdby userid,
  modified datetime,
  modifiedby userid,

  CONSTRAINT PK_animalMapping PRIMARY KEY (rowid)
);