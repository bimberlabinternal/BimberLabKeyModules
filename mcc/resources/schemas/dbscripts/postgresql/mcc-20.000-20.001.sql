CREATE TABLE mcc.userRequests (
   rowid serial,
   email varchar(1000),
   firstName varchar(1000),
   lastName varchar(1000),
   title varchar(1000),
   institution varchar(1000),
   reason varchar(4000),
   userid userid,

   container entityid,
   created timestamp,
   createdby userid,
   modified timestamp,
   modifiedby userid,

   CONSTRAINT PK_userRequests PRIMARY KEY (rowid)
);