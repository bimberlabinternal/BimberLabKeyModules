ALTER TABLE mGAP.releaseTracks ADD vcfId int;

CREATE TABLE mGAP.tracksPerRelease (
  rowid int identity(1,1),
  releaseId entityid,
  trackName varchar(100),
  label varchar(1000),
  category varchar(1000),
  url varchar(4000),
  description varchar(4000),
  vcfId int,
  isprimarytrack bit,

  container entityid,
  created datetime,
  createdby userid,
  modified datetime,
  modifiedby userid,

  CONSTRAINT PK_tracksPerRelease PRIMARY KEY (rowid)
);