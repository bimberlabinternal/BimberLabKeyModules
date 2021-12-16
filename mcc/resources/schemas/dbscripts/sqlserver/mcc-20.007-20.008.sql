CREATE TABLE mcc.animalMapping (
    rowid int IDENTITY(1,1),
    subjectname varchar(1000),
    externalAlias varchar(1000),
    otherNames varchar(1000),
    biosample_accession varchar(1000),

    container entityid,
    created datetime,
    createdby userid,
    modified datetime,
    modifiedby userid,

    CONSTRAINT PK_animalMapping PRIMARY KEY (rowid)
);