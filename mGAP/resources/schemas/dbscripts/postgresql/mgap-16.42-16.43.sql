ALTER TABLE mGAP.releaseTracks ADD vcfId int;

CREATE TABLE mGAP.tracksPerRelease (
  rowid serial,
  releaseId entityid,
  trackName varchar(100),
  label varchar(1000),
  category varchar(1000),
  url varchar(4000),
  description varchar(4000),
  vcfId int,
  isprimarytrack boolean,

  container entityid,
  created timestamp,
  createdby userid,
  modified timestamp,
  modifiedby userid,

  CONSTRAINT PK_tracksPerRelease PRIMARY KEY (rowid)
);