ALTER TABLE variantdb.variantattributes ADD batchId ENTITYID;

ALTER TABLE variantdb.VariantLiftover ADD referenceVariantId ENTITYID;
ALTER TABLE variantdb.VariantLiftover ADD referenceAlleleId ENTITYID;
