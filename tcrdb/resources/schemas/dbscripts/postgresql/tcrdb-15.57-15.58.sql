CREATE TABLE tcrdb.repertoire_stats (
  rowid int SERIAL,
  cdna_id int,
  metricName varchar(1000),
  value double precision,
  qualValue varchar(4000),
  comment varchar(4000),

  container entityid,
  created timestamp,
  createdby int,
  modified timestamp,
  modifiedby int,

  constraint PK_repertoire_stats PRIMARY KEY (rowid)
);