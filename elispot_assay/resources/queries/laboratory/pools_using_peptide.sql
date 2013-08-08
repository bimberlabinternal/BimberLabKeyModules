SELECT
p.rowid,
GROUP_CONCAT(distinct pm.poolid.pool_name, chr(10)) as pools
FROM laboratory.peptides p
JOIN elispot_assay.peptide_pool_members pm ON (p.sequence = pm.sequence)
GROUP BY p.rowid