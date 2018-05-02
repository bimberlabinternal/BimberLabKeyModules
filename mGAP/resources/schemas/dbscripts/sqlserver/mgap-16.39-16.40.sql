
--add columns to store variant table
ALTER TABLE mGAP.variantCatalogReleases ADD variantTable int;
ALTER TABLE mGAP.variantCatalogReleases ADD objectid ENTITYID;
GO
UPDATE mGAP.variantCatalogReleases SET objectid = newid();

-- add table for variant list
CREATE TABLE mGAP.variantList (
  rowid int identity(1,1),
  releaseId entityid,
  contig varchar(100),
  position int,
  reference varchar(100),
  allele varchar(100),
  source varchar(1000),
  reason varchar(1000),
  description varchar(4000),
  overlappingGenes varchar(4000),

  objectid entityid,
  container entityid,
  created datetime,
  createdby userid,
  modified datetime,
  modifiedby userid,

  CONSTRAINT PK_variantList PRIMARY KEY (objectid)
);

--add table for release stats
CREATE TABLE mGAP.releaseStats (
  rowid int identity(1,1),
  releaseId entityid,
  category varchar(100),
  metricName varchar(100),
  value DOUBLE PRECISION,

  objectid entityid,
  container entityid,
  created datetime,
  createdby userid,
  modified datetime,
  modifiedby userid,

  CONSTRAINT PK_releaseStats PRIMARY KEY (objectid)
);