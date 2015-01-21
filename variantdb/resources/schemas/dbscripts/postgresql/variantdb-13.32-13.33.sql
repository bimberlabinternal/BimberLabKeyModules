ALTER TABLE variantdb.ReferenceVariants ALTER COLUMN allele TYPE VARCHAR(1000);
ALTER TABLE variantdb.ReferenceVariants ALTER COLUMN reference TYPE VARCHAR(1000);

ALTER TABLE variantdb.VariantLiftover ALTER COLUMN allele TYPE VARCHAR(1000);
ALTER TABLE variantdb.VariantLiftover ALTER COLUMN reference TYPE VARCHAR(1000);

ALTER TABLE variantdb.UploadBatches DROP type;
ALTER TABLE variantdb.UploadBatches ADD COLUMN rowid serial;