ALTER TABLE tcrdb.cdnas ADD citeseqReadsetId int;
ALTER TABLE tcrdb.cdnas ADD citeseqPanel varchar(100);

CREATE TABLE tcrdb.citeseq_panels (
  rowid int serial,
  name varchar(100),
  antibody varchar(100),

  container entityid,
  created timestamp,
  createdby int,
  modified timestamp,
  modifiedby int,

  constraint PK_citeseq_panels PRIMARY KEY (rowid)
);

CREATE TABLE tcrdb.citeseq_antibodies (
  rowid int serial,
  antibodyName varchar(100),
  markerName varchar(100),
  cloneName varchar(100),
  vendor varchar(100),
  productId varchar(100),
  barcodeName varchar(100),  
  adaptersequence varchar(4000),
  
  container entityid,
  created timestamp,
  createdby int,
  modified timestamp,
  modifiedby int,
  
  constraint PK_citeseq_antibodies PRIMARY KEY (rowid)
);