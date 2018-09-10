CREATE TABLE mGAP.releaseTracks (
  rowid int identity(1,1),
  trackName varchar(100),
  label varchar(1000),
  category varchar(1000),
  url varchar(4000),
  description varchar(4000),
  isprimarytrack bit,

  container entityid,
  created datetime,
  createdby userid,
  modified datetime,
  modifiedby userid,

  CONSTRAINT PK_releaseTracks PRIMARY KEY (rowid)
);

CREATE TABLE mGAP.releaseTrackSubsets (
  rowid int identity(1,1),
  trackName varchar(100),
  subjectId varchar(100),

  container entityid,
  created datetime,
  createdby userid,
  modified datetime,
  modifiedby userid,

  CONSTRAINT PK_releaseTrackSubsets PRIMARY KEY (rowid)
);

ALTER TABLE mGAP.variantList ADD af double precision;
