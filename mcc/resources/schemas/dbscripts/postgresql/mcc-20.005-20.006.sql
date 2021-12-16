ALTER TABLE mcc.animalRequests ADD COLUMN iacucprotocol VARCHAR(200);
ALTER TABLE mcc.animalRequests ADD COLUMN grantnumber VARCHAR(200);
ALTER TABLE mcc.animalRequests ADD COLUMN breedingpurpose VARCHAR(4000);

ALTER TABLE mcc.animalRequests DROP COLUMN ofinterestcenters;
ALTER TABLE mcc.animalRequests DROP COLUMN numberofanimals;
ALTER TABLE mcc.animalRequests DROP COLUMN othercharacteristics;

CREATE TABLE mcc.requestcohorts (
    rowid SERIAL,
    requestid VARCHAR(40),
    numberofanimals int,
    sex VARCHAR(100),
    othercharacteristics VARCHAR(4000),

    container ENTITYID,
    created timestamp,
    createdby int,
    modified timestamp,
    modifiedby int,

    CONSTRAINT PK_requestcohorts PRIMARY KEY (rowid)
);