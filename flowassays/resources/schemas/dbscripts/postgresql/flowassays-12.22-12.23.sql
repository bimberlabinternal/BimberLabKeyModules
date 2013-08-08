CREATE TABLE flowassays.fcsfilemetadata (
  rowid serial,
  fcsId int,
  subjectId varchar(100),
  date timestamp,
  category varchar(100),
  tissue varchar(100),
  sampleType varchar(100),
  treatment varchar(100),

  sampleId int,
  comment text,

  container entityid,
  createdBy int,
  created timestamp,
  modifiedBy int,
  modified timestamp,

  CONSTRAINT PK_fcsfilemetadata PRIMARY KEY (rowid)
);
