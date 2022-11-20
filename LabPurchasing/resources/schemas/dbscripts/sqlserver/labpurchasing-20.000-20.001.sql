CREATE TABLE labpurchasing.purchases (
    rowId int identity(1,1),
    orderNumber integer,
    requestor varchar(255),

    vendorId int,
    itemName varchar(2000),
    itemNumber varchar(2000),
    units varchar(200),
    quantity int,
    unitCost numeric,
    totalCost numeric,
    description varchar(2000),
    fundingSource int,

    status varchar(1000),

    orderNumber varchar(255),
    orderedBy varchar(1000),
    orderDate datetime,

    receivedBy varchar(1000),
    receivedDate datetime,
    itemLocation varchar(1000),

    invoiceNumber varchar(255),
    invoiceDate datetime,
    invoicedBy varchar(255),

    container entityid,
    created datetime,
    modified datetime,
    createdBy int,
    modifiedBy int,

    CONSTRAINT PK_purchases PRIMARY KEY (rowid)
);

CREATE TABLE labpurchasing.vendors (
     rowid int identity(1,1),

     vendorName varchar(255),
     phone varchar(255),
     fax varchar(255),
     email varchar(255),
     url varchar(255),
     address varchar(4000),
     address2 varchar(4000),
     city varchar(1000),
     state varchar(50),
     zip varchar(100),
     country varchar(100),
     comments varchar(2000),
     accountNumber varchar(1000),
     enabled bit DEFAULT 1,

     container entityid,
     created datetime,
     modified datetime,
     createdBy int,
     modifiedBy int,

     CONSTRAINT PK_vendors PRIMARY KEY (rowid)
);

CREATE TABLE labpurchasing.referenceItems (
    rowId int identity(1,1),
    vendorId int,
    itemName varchar(2000),
    itemNumber varchar(2000),
    units varchar(200),
    unitCost numeric,

    container entityid,
    created datetime,
    modified datetime,
    createdBy int,
    modifiedBy int,

    CONSTRAINT PK_referenceItems PRIMARY KEY (rowid)
)

CREATE TABLE labpurchasing.fundingSources (
    rowId int identity(1,1),

    title varchar(4000),
    "grant" varchar(4000),
    accountNumber varchar(1000),
    projectNumber varchar(1000),

    startDate datetime,
    endDate datetime,
    pi varchar(1000),
    comment varchar(4000),

    container entityid,
    created datetime,
    modified datetime,
    createdBy int,
    modifiedBy int,

    CONSTRAINT PK_fundingSources PRIMARY KEY (rowid)
);

CREATE TABLE labpurchasing.purchasingUnits (
    rowId int identity(1,1),

    unit varchar(4000),

    container entityid,
    created timestamp,
    modified timestamp,
    createdBy int,
    modifiedBy int,

    CONSTRAINT PK_purchasingUnits PRIMARY KEY (rowid)
);
