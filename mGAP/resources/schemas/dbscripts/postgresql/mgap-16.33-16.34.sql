--table of releases:
CREATE TABLE mGAP.variantCatalogReleases (
  rowid serial,
  version varchar(100),
  releaseDate timestamp,
  vcfId int,
  totalSubjects int,
  totalVariants int,
  dbSnpId varchar(100),

  container entityid,
  created timestamp,
  createdby userid,
  modified timestamp,
  modifiedby userid,

  CONSTRAINT PK_variantCatalogReleases PRIMARY KEY (rowid)
);

--table of sequence datasets:
CREATE TABLE mGAP.sequenceDatasets (
  rowid serial,
  mgapId varchar(100),
  totalReads int,
  sraAccession varchar(100),

  container entityid,
  created timestamp,
  createdby userid,
  modified timestamp,
  modifiedby userid,

  CONSTRAINT PK_sequenceDatasets PRIMARY KEY (rowid)
);

