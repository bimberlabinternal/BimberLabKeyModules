CREATE TABLE mGAP.annotations (
  rowid serial,
  category varchar(1000),
  label varchar(500),
  datasource varchar(500),
  datatype varchar(500),
  datanumber varchar(500),
  infoKey varchar(100),
  url varchar(4000),
  description varchar(4000),

  container entityid,
  created timestamp,
  createdby userid,
  modified timestamp,
  modifiedby userid,

  CONSTRAINT PK_annotations PRIMARY KEY (rowid)
);