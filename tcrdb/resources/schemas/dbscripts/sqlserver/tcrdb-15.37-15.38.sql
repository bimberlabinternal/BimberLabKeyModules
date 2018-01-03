ALTER TABLE tcrdb.stims ADD numEffectors int;
ALTER TABLE tcrdb.stims DROP COLUMN effectors;
ALTER TABLE tcrdb.stims ADD status varchar(200);

CREATE TABLE tcrdb.cdnas (
  rowid int IDENTITY(1,1),
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
  created datetime,
  createdby int,
  modified datetime,
  modifiedby int,

  constraint PK_cdnas PRIMARY KEY (rowid)
);