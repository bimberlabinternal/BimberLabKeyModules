CREATE INDEX IDX_ReferenceVariants_dbSnpAccession ON variantdb.referencevariants (dbSnpAccession);

CREATE INDEX IDX_ReferenceVariantAlleles_referenceId ON variantdb.referencevariantalleles (referenceVariantId, allele);
CREATE INDEX IDX_VariantAttributes_referenceId ON variantdb.variantattributes (referenceVariantId, referenceAlleleId, attributeid);

--ALTER TABLE variantdb.referencevariants ADD CONSTRAINT UNIQUE_ReferenceVariants_dbSnpAccession UNIQUE (dbSnpAccession)
-- WHERE dbSnpAccession IS NOT NULL;

ALTER TABLE variantdb.Variants ALTER COLUMN allele VARCHAR(1000);
ALTER TABLE variantdb.Variants ALTER COLUMN reference VARCHAR(1000);