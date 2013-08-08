ALTER TABLE elispot_assay.peptide_pools add column container entityid;
UPDATE elispot_assay.peptide_pools
SET container = (select entityid from core.containers c where c.name = 'Shared' and (select parent from core.Containers c2 where c2.EntityId = c.parent) is null)
WHERE container is null;

ALTER TABLE elispot_assay.peptide_pool_members add column container entityid;
UPDATE elispot_assay.peptide_pool_members
SET container = (select entityid from core.containers c where c.name = 'Shared' and (select parent from core.Containers c2 where c2.EntityId = c.parent) is null)
WHERE container is null;