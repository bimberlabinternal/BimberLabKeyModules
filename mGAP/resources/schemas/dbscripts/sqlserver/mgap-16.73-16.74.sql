ALTER TABLE mGAP.variantCatalogReleases ADD species varchar(1000);
ALTER TABLE mGAP.releaseTracks ADD species varchar(1000);
ALTER TABLE mGAP.releaseTracks DROP COLUMN mergepriority;