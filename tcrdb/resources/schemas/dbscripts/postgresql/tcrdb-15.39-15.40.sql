ALTER TABLE tcrdb.peptides ADD COLUMN category varchar(200);
ALTER TABLE tcrdb.peptides ADD COLUMN type varchar(200);

CREATE TABLE tcrdb.plate_processing (
  rowid serial,
  plateId varchar(100),
  type varchar(100),

  container entityid,
  created timestamp,
  createdby int,
  modified timestamp,
  modifiedby int,

  constraint PK_plate_processing PRIMARY KEY (rowid)
);