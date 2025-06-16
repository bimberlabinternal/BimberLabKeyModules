CREATE TABLE tcrdb.stims (
  rowid SERIAL,
  cdna_id int,
  controlStimId int,

  quantificationMethod varchar(1000),
  quantification double precision,

  flowQuantificationMethod varchar(1000),
  flowQuantification double precision,
  comment varchar(4000),

  container entityid,
  created timestamp,
  createdby int,
  modified timestamp,
  modifiedby int,

  constraint PK_stims PRIMARY KEY (rowid)
);