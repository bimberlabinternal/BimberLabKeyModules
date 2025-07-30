CREATE TABLE tcrdb.repertoire_stats (
  rowid int IDENTITY(1,1),
  cdna_id int,
  metricName varchar(1000),
  value double precision,
  qualValue varchar(4000),
  comment varchar(4000),

  container entityid,
  created datetime,
  createdby int,
  modified datetime,
  modifiedby int,

  constraint PK_repertoire_stats PRIMARY KEY (rowid)
);