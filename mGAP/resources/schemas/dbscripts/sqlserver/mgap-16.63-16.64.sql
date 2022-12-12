CREATE TABLE mGAP.geneticMeasurements (
  rowid int identity(1,1),
  sampleName varchar(500),
  measurementName varchar(500),
  measurementValue double precision,
  qualvalue varchar(500),
  comment varchar(4000),

  container entityid,
  created datetime,
  createdby userid,
  modified datetime,
  modifiedby userid,

  CONSTRAINT PK_geneticMeasurements PRIMARY KEY (rowid)
);