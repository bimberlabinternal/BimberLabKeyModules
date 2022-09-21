ALTER TABLE mcc.requestReviews add role nvarchar(200);

ALTER TABLE mcc.animalrequests add terminalProcedures bit;

ALTER TABLE mcc.animalrequests add breedinganimals nvarchar(200);
GO
UPDATE mcc.animalrequests SET breedinganimals = 'Will not breed' WHERE breedinganimals = 0;
UPDATE mcc.animalrequests SET breedinganimals = 'Request breeding pair' WHERE breedinganimals = 1;
ALTER TABLE mcc.animalrequests drop column isbreedinganimals;

ALTER TABLE mcc.animalrequests add narrative nvarchar(MAX);
ALTER TABLE mcc.animalrequests add title nvarchar(MAX);
ALTER TABLE mcc.animalrequests add neuroscience nvarchar(MAX);
ALTER TABLE mcc.animalrequests add census bit;
ALTER TABLE mcc.animalrequests add censusReason nvarchar(MAX);
