ALTER TABLE mcc.requestReviews add role varchar(200);

ALTER TABLE mcc.animalrequests add terminalProcedures boolean;

ALTER TABLE mcc.animalrequests add breedinganimals varchar(200);

UPDATE mcc.animalrequests SET breedinganimals = 'Will not breed' WHERE isbreedinganimals = FALSE;
UPDATE mcc.animalrequests SET breedinganimals = 'Request breeding pair' WHERE isbreedinganimals = TRUE;
ALTER TABLE mcc.animalrequests drop column isbreedinganimals;

ALTER TABLE mcc.animalrequests add narrative varchar(4000);
ALTER TABLE mcc.animalrequests add title varchar(4000);
ALTER TABLE mcc.animalrequests add neuroscience varchar(4000);
ALTER TABLE mcc.animalrequests add census boolean;
ALTER TABLE mcc.animalrequests add censusReason varchar(4000);
