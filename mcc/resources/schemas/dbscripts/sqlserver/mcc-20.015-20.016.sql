CREATE TABLE mcc.census (
    rowid int identity(1,1),
    yearNo int,
    startdate datetime,
    enddate datetime,
    centerName varchar(1000),
    totalBreedingPairs int,
    totalLivingOffspring int,
    survivalRates int,
    marmosetsShipped int,

    container entityid,
    created datetime,
    createdby userid,
    modified datetime,
    modifiedby userid,

    CONSTRAINT PK_census PRIMARY KEY (rowid)
);