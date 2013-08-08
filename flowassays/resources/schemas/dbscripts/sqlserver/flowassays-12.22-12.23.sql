CREATE TABLE flowassays.fcsfilemetadata (
  rowid int identity(1,1),
  fcsId int,
  subjectId varchar(100),
  date datetime,
  category varchar(100),
  tissue varchar(100),
  sampleType varchar(100),
  treatment varchar(100),

  sampleId int,
  comment text,

  container entityid,
  createdBy int,
  created datetime,
  modifiedBy int,
  modified datetime,

  CONSTRAINT PK_fcsfilemetadata PRIMARY KEY (rowid)
);
