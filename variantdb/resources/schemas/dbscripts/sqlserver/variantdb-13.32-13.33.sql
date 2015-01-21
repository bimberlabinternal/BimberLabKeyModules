ALTER TABLE variantdb.ReferenceVariants ALTER COLUMN allele VARCHAR(1000);
ALTER TABLE variantdb.ReferenceVariants ALTER COLUMN reference VARCHAR(1000);

ALTER TABLE variantdb.VariantLiftover ALTER COLUMN allele VARCHAR(1000);
ALTER TABLE variantdb.VariantLiftover ALTER COLUMN reference VARCHAR(1000);

ALTER TABLE variantdb.UploadBatches DROP COLUMN type;
ALTER TABLE variantdb.UploadBatches ADD rowid int identity(1,1);