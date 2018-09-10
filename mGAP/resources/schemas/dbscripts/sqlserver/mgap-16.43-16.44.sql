ALTER TABLE mGAP.releaseTracks ADD source VARCHAR(1000);
ALTER TABLE mGAP.tracksPerRelease ADD source VARCHAR(1000);

ALTER TABLE mGAP.variantList ADD omim VARCHAR(2000);
