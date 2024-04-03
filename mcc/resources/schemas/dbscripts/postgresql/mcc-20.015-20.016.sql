CREATE TABLE mcc.census (
    rowid serial,
    yearNo int,
    startdate timestamp,
    enddate timestamp,
    centerName varchar(1000),
    totalBreedingPairs int,
    totalLivingOffspring int,
    survivalRates int,
    marmosetsShipped int,

    container entityid,
    created timestamp,
    createdby userid,
    modified timestamp,
    modifiedby userid,

    CONSTRAINT PK_census PRIMARY KEY (rowid)
);