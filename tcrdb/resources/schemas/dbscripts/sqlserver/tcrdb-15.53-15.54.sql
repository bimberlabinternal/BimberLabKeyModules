CREATE TABLE tcrdb.stims (
  rowid int identity(1,1),
  cdna_id int,
  controlStimId int,

  quantificationMethod varchar(1000),
  quantification double precision,

  flowQuantificationMethod varchar(1000),
  flowQuantification double precision,
  comment varchar(4000),

  container entityid,
  created datetime,
  createdby int,
  modified datetime,
  modifiedby int,

  constraint PK_stims PRIMARY KEY (rowid)
);