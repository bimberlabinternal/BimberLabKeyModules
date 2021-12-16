CREATE TABLE mcc.animalMapping (
    rowid SERIAL,
    subjectname varchar(1000),
    externalAlias varchar(1000),
    otherNames varchar(1000),
    biosample_accession varchar(1000),

    container entityid,
    created timestamp ,
    createdby userid,
    modified timestamp ,
    modifiedby userid,

    CONSTRAINT PK_animalMapping PRIMARY KEY (rowid)
);