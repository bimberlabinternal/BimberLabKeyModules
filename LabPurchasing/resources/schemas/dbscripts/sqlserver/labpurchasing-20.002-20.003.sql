ALTER TABLE labpurchasing.purchases ADD purchaseOrder varchar(1000);

CREATE TABLE labpurchasing.purchasingLocations (
    rowId int identity(1,1),

    location varchar(4000),

    container entityid,
    created datetime,
    modified datetime,
    createdBy int,
    modifiedBy int,

    CONSTRAINT PK_purchasingLocations PRIMARY KEY (rowid)
);