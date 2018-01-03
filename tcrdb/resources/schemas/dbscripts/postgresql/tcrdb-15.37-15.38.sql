ALTER TABLE tcrdb.stims ADD COLUMN numEffectors int;
ALTER TABLE tcrdb.stims DROP COLUMN effectors;
ALTER TABLE tcrdb.stims ADD status varchar(200);

CREATE TABLE tcrdb.cdnas (
  rowid serial,
  sortid int,
  chemistry varchar(200),
  concentration double precision,
  plateId varchar(200),
  well varchar(100),

  readsetid int,
  enrichedreadsetid int,
  comment varchar(4000),
  status varchar(200),
  lsid LsidType,

  container entityid,
  created timestamp,
  createdby int,
  modified timestamp,
  modifiedby int,

  constraint PK_cdnas PRIMARY KEY (rowid)
);