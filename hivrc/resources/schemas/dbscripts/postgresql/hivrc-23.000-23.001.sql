CREATE TABLE hivrc.datasets (
    rowid SERIAL,
    label varchar(1000),
    description varchar(4000),

    datatype varchar(1000),
    dataowner varchar(1000),
    fileid int,

    container entityid,
    created timestamp,
    createdby int,
    modified timestamp,
    modifiedby int,

    constraint PK_datasets PRIMARY KEY (rowid)
);