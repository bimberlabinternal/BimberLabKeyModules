ALTER TABLE variantdb.VariantAttributes DROP COLUMN variantId;
ALTER TABLE variantdb.VariantAttributes ADD referenceVariantId ENTITYID;
ALTER TABLE variantdb.VariantAttributes ADD referenceAlleleId ENTITYID;

ALTER TABLE variantdb.VariantAttributes ADD value varchar(1000);