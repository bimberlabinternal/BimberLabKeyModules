CREATE TABLE mGAP.geneticMeasurements (
  rowid serial,
  sampleName varchar(500),
  measurementName varchar(500),
  measurementValue double precision,
  qualvalue varchar(500),
  comment varchar(4000),

  container entityid,
  created timestamp,
  createdby userid,
  modified timestamp,
  modifiedby userid,

  CONSTRAINT PK_geneticMeasurements PRIMARY KEY (rowid)
);