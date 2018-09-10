CREATE TABLE mGAP.releaseTracks (
  rowid serial,
  trackName varchar(100),
  label varchar(1000),
  category varchar(1000),
  url varchar(4000),
  description varchar(4000),
  isprimarytrack boolean,

  container entityid,
  created timestamp,
  createdby userid,
  modified timestamp,
  modifiedby userid,

  CONSTRAINT PK_releaseTracks PRIMARY KEY (rowid)
);

CREATE TABLE mGAP.releaseTrackSubsets (
  rowid serial,
  trackName varchar(100),
  subjectId varchar(100),

  container entityid,
  created timestamp,
  createdby userid,
  modified timestamp,
  modifiedby userid,

  CONSTRAINT PK_releaseTrackSubsets PRIMARY KEY (rowid)
);

ALTER TABLE mGAP.variantList ADD af double precision;