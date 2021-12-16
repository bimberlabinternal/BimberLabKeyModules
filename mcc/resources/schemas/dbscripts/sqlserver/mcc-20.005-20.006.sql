ALTER TABLE mcc.animalRequests ADD iacucprotocol VARCHAR(200);
ALTER TABLE mcc.animalRequests ADD grantnumber VARCHAR(200);
ALTER TABLE mcc.animalRequests ADD breedingpurpose VARCHAR(4000);

ALTER TABLE mcc.animalRequests DROP COLUMN ofinterestcenters;
ALTER TABLE mcc.animalRequests DROP COLUMN numberofanimals;
ALTER TABLE mcc.animalRequests DROP COLUMN othercharacteristics;

CREATE TABLE mcc.requestcohorts (
    rowid int identity(1,1),
    requestid VARCHAR(40),
    numberofanimals int,
    sex VARCHAR(100),
    othercharacteristics NVARCHAR(max),

    container ENTITYID,
    created datetime,
    createdby int,
    modified datetime,
    modifiedby int,

    CONSTRAINT PK_requestcohorts PRIMARY KEY (rowid)
);