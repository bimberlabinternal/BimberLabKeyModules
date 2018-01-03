CREATE TABLE tcrdb.stims (
  rowid int identity(1,1),
  animalId varchar(100),
  date datetime,
  stim varchar(4000),
  treatment varchar(4000),
  background double precision,
  activated double precision,
  comment varchar(4000),
  lsid LsidType,

  container entityid,
  created datetime,
  createdby int,
  modified datetime,
  modifiedby int,

  constraint PK_stims PRIMARY KEY (rowid)
);

CREATE TABLE tcrdb.sorts (
  rowid int identity(1,1),
  stimid int,
  population varchar(1000),
  replicate varchar(100),
  cells int,
  readsetid int,
  enrichedreadsetid int,
  comment varchar(4000),
  lsid LsidType,

  container entityid,
  created datetime,
  createdby int,
  modified datetime,
  modifiedby int,

  constraint PK_sorts PRIMARY KEY (rowid)
);

CREATE TABLE tcrdb.peptides (
  stim varchar(200),

  container entityid,
  created datetime,
  createdby int,
  modified datetime,
  modifiedby int,

  constraint PK_peptides PRIMARY KEY (stim)
);