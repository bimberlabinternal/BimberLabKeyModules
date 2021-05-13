CREATE TABLE mcc.etltranslations (
   rowid int IDENTITY(1,1),
   schemaName varchar(1000),
   queryName varchar(1000),
   columnName varchar(1000),
   sourceVal varchar(1000),
   transformedVal varchar(1000),

   container entityid,
   created datetime,
   createdby userid,
   modified datetime,
   modifiedby userid,

   CONSTRAINT PK_etltranslations PRIMARY KEY (rowid)
);