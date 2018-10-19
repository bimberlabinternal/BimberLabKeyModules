CREATE TABLE mGAP.phenotypes (
  rowid serial,
  releaseId entityid,
  omim_phenotype varchar(4000),
  omim_entry varchar(1000),
  omim varchar(1000),

  container entityid,
  created timestamp,
  createdby userid,
  modified timestamp,
  modifiedby userid,

  CONSTRAINT PK_phenotypes PRIMARY KEY (rowid)
);
