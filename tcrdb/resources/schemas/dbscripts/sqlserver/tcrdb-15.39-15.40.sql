ALTER TABLE tcrdb.peptides ADD category varchar(200);
ALTER TABLE tcrdb.peptides ADD type varchar(200);

CREATE TABLE tcrdb.plate_processing (
  rowid int identity(1,1),
  plateId varchar(100),
  type varchar(100),

  container entityid,
  created datetime,
  createdby int,
  modified datetime,
  modifiedby int,

  constraint PK_plate_processing PRIMARY KEY (rowid)
);