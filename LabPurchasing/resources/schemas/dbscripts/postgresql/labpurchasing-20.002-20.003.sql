ALTER TABLE labpurchasing.purchases ADD purchaseOrder varchar(1000);

CREATE TABLE labpurchasing.purchasingLocations (
    rowId serial,

    location varchar(4000),

    container entityid,
    created timestamp,
    modified timestamp,
    createdBy int,
    modifiedBy int,

    CONSTRAINT PK_purchasingLocations PRIMARY KEY (rowid)
);