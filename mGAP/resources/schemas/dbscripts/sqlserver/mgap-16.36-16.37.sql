ALTER TABLE mGAP.variantCatalogReleases DROP COLUMN sequenceType;
ALTER TABLE mGAP.sequenceDatasets ADD sequenceType varchar(100);