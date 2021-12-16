ALTER TABLE covidseq.samples ADD assayType varchar(200);
ALTER TABLE covidseq.samples ADD N1_or_S double precision;
ALTER TABLE covidseq.samples ADD N2_orN double precision;
ALTER TABLE covidseq.samples ADD RP_or_ORF1ab double precision;
ALTER TABLE covidseq.samples ADD MS2 double precision;
ALTER TABLE covidseq.samples ADD cDNA_Plate_ID varchar(200);
ALTER TABLE covidseq.samples ADD cDNA_Plate_Location varchar(200);

ALTER TABLE covidseq.samples DROP COLUMN country;
ALTER TABLE covidseq.samples DROP COLUMN county;

ALTER TABLE covidseq.samples DROP COLUMN patientId;
GO
ALTER TABLE covidseq.samples ADD patientId ENTITYID;

CREATE TABLE covidseq.patients (
    rowid int IDENTITY(1,1),
    identifier varchar(1000),
    patientId ENTITYID,

    "state" varchar(1000),
    county varchar(2000),
    country varchar(2000),
    age double precision,

    container entityid,
    created datetime,
    createdby int,
    modified datetime,
    modifiedby int,

    constraint PK_patients PRIMARY KEY (patientId)
)