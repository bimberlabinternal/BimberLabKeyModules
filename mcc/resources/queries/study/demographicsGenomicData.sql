SELECT

d.Id,

GROUP_CONCAT(distinct d.datatype, ',') as datatypes,
GROUP_CONCAT(distinct d.sra_accession, chr(10)) as sra_accession,

FROM study.genomicDatasets d
WHERE d.qcstate.publicdata = true
GROUP BY d.Id