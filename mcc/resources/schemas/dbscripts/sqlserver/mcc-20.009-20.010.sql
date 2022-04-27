ALTER TABLE mcc.animalRequests drop column isprincipalinvestigator;

ALTER TABLE mcc.animalRequests DROP COLUMN certify;
ALTER TABLE mcc.animalRequests ADD certify bit;