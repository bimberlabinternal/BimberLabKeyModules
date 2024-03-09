CREATE TABLE hivrc.datasets (
    rowid int IDENTITY(1,1),
    label varchar(1000),
    description varchar(4000),

    datatype varchar(1000),
    dataowner varchar(1000),
    fileid int,

    container entityid,
    created datetime,
    createdby int,
    modified datetime,
    modifiedby int,

    constraint PK_datasets PRIMARY KEY (rowid)
);