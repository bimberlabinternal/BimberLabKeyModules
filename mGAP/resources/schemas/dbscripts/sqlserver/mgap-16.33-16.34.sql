--table of releases:
CREATE TABLE mGAP.variantCatalogReleases (
  rowid int IDENTITY(1,1),
  version varchar(100),
  releaseDate datetime,
  vcfId int,
  totalSubjects int,
  totalVariants int,
  dbSnpId varchar(100),

  container entityid,
  created datetime,
  createdby userid,
  modified datetime,
  modifiedby userid,

  CONSTRAINT PK_variantCatalogReleases PRIMARY KEY (rowid)
);

--table of sequence datasets:
CREATE TABLE mGAP.sequenceDatasets (
  rowid int IDENTITY(1,1),
  mgapId varchar(100),
  totalReads int,
  sraAccession varchar(100),

  container entityid,
  created datetime,
  createdby userid,
  modified datetime,
  modifiedby userid,

  CONSTRAINT PK_sequenceDatasets PRIMARY KEY (rowid)
);

