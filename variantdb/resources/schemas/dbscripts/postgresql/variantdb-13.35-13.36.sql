CREATE UNIQUE INDEX IDX_ReferenceVariants_dbSnpAccession ON variantdb.referencevariants (dbSnpAccession);

CREATE INDEX IDX_ReferenceVariantAlleles_referenceId ON variantdb.referencevariantalleles (referenceVariantId, allele);
CREATE INDEX IDX_VariantAttributes_referenceId ON variantdb.variantattributes (referenceVariantId, referenceAlleleId, attributeid);

--handled using unique index above
--ALTER TABLE variantdb.referencevariants ADD CONSTRAINT UNIQUE_ReferenceVariants_dbSnpAccession UNIQUE (dbSnpAccession);

ALTER TABLE variantdb.Variants ALTER COLUMN allele TYPE VARCHAR(1000);
ALTER TABLE variantdb.Variants ALTER COLUMN reference TYPE VARCHAR(1000);
