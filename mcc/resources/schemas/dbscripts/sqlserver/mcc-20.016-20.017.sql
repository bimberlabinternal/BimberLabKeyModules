ALTER TABLE mcc.census DROP COLUMN totalLivingOffspring;

ALTER TABLE mcc.census ADD totalOffspring int;
ALTER TABLE mcc.census ADD totalOffspringU24 int;
ALTER TABLE mcc.census ADD totalBreedingPairsU24 int;