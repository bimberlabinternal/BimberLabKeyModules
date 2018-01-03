CREATE TABLE tcrdb.stims (
  rowid serial,
  animalId varchar(100),
  date timestamp,
  stim varchar(4000),
  treatment varchar(4000),
  background double precision,
  activated double precision,
  comment varchar(4000),
  lsid LsidType,

  container entityid,
  created timestamp,
  createdby int,
  modified timestamp,
  modifiedby int,

  constraint PK_stims PRIMARY KEY (rowid)
);

CREATE TABLE tcrdb.sorts (
  rowid serial,
  stimid int,
  population varchar(1000),
  replicate varchar(100),
  cells int,
  readsetid int,
  enrichedreadsetid int,
  comment varchar(4000),
  lsid LsidType,

  container entityid,
  created timestamp,
  createdby int,
  modified timestamp,
  modifiedby int,

  constraint PK_sorts PRIMARY KEY (rowid)
);

CREATE TABLE tcrdb.peptides (
  stim varchar(200),

  container entityid,
  created timestamp,
  createdby int,
  modified timestamp,
  modifiedby int,

  constraint PK_peptides PRIMARY KEY (stim)
);