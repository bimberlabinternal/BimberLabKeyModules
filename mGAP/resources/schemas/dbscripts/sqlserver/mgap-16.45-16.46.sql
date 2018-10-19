CREATE TABLE mGAP.phenotypes (
  rowid int identity(1,1),
  releaseId entityid,
  omim_phenotype varchar(4000),
  omim_entry varchar(1000),
  omim varchar(1000),

  container entityid,
  created datetime,
  createdby userid,
  modified datetime,
  modifiedby userid,

  CONSTRAINT PK_phenotypes PRIMARY KEY (rowid)
);
