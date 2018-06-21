CREATE TABLE tcrdb.clones (
  rowid int identity(1,1),
  cloneName varchar(100),
  chain varchar(100),
  cdr3 varchar(100),
  vGene varchar(100),
  dGene varchar(100),
  jGene varchar(100),
  cGene varchar(100),

  comments varchar(4000),

  container entityid,
  created datetime,
  createdby int,
  modified datetime,
  modifiedby int,

  constraint PK_clones PRIMARY KEY (rowid)
);

CREATE TABLE tcrdb.clone_responses (
  rowid int identity(1,1),
  experiment varchar(100),
  cloneName varchar(100),
  cellBackground varchar(100),
  date varchar(100),
  numEffectors int,
  pctTransduction double precision,

  stim varchar(100),
  costim varchar(100),
  antigen varchar(100),
  activationFrequency double precision,
  backgroundFrequency double precision,

  comments varchar(4000),

  container entityid,
  created datetime,
  createdby int,
  modified datetime,
  modifiedby int,

  constraint PK_clone_responses PRIMARY KEY (rowid)
);