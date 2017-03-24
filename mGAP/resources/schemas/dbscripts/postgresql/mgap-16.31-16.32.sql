CREATE TABLE mGAP.animalMapping (
  rowid serial,
  subjectname varchar(100),
  externalAlias varchar(100),

  container entityid,
  created timestamp,
  createdby userid,
  modified timestamp,
  modifiedby userid,

  CONSTRAINT PK_animalMapping PRIMARY KEY (rowid)
);