CREATE TABLE mcc.requestReviews (
    rowid SERIAL,
    reviewerId int,
    review varchar(1000),
    score int,
    comments varchar(4000),
    status varchar(1000),
    requestid ENTITYID,

    container entityid,
    created timestamp,
    createdby userid,
    modified timestamp,
    modifiedby userid,

    CONSTRAINT PK_requestReviews PRIMARY KEY (rowid)
);

CREATE TABLE mcc.requestScores (
    rowid SERIAL,
    preliminaryScore int,
    resourceAvailabilityScore varchar(200),
    researchAreaPriorityScore  varchar(200),

    proposalScore varchar(200),
    comments varchar(4000),
    requestid ENTITYID,

    container entityid,
    created timestamp,
    createdby userid,
    modified timestamp,
    modifiedby userid,

    CONSTRAINT PK_requestScores PRIMARY KEY (rowid)
);

ALTER TABLE mcc.animalRequests add earlystageinvestigator boolean;