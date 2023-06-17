CREATE TABLE mGAP.annotations (
  rowid int identity(1,1),
  category varchar(1000),
  label varchar(500),
  datasource varchar(500),
  datatype varchar(500),
  datanumber varchar(500),
  infoKey varchar(100),
  url varchar(4000),
  description varchar(4000),

  container entityid,
  created datetime,
  createdby userid,
  modified datetime,
  modifiedby userid,

  CONSTRAINT PK_annotations PRIMARY KEY (rowid)
);