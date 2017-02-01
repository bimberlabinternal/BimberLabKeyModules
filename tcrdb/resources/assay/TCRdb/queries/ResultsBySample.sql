SELECT
  group_concat(distinct d.rowId) as pks,
  d.subjectId,
  d.sampleName,
  d.analysisId,
  max(d.date) as date,
  group_concat(distinct d.locus, '/') as loci,
  count(distinct d.locus) as numLoci,

  group_concat(distinct CASE WHEN d.locus = 'TRA' THEN d.cdr3 ELSE null END, chr(10)) as TRA_CDR3,
  group_concat(distinct CASE WHEN d.locus = 'TRA' THEN d.cdr3 ELSE null END, ';') as TRA_CDR3_URL,
  group_concat(distinct CASE WHEN d.locus = 'TRA' THEN d.vGene ELSE null END, chr(10)) as TRA_V,
  group_concat(distinct CASE WHEN d.locus = 'TRA' THEN d.dGene ELSE null END, chr(10)) as TRA_D,
  group_concat(distinct CASE WHEN d.locus = 'TRA' THEN d.jGene ELSE null END, chr(10)) as TRA_J,
  group_concat(distinct CASE WHEN d.locus = 'TRA' THEN d.libraryId.species ELSE null END, chr(10)) as TRA_Species,
  sum(CASE WHEN d.locus = 'TRA' THEN 1 ELSE 0 END) as TRA_Count,
  group_concat(distinct CASE WHEN d.locus = 'TRA' THEN (d.cdr3 || ' (' || CAST(ROUND(d.fraction, 3) AS VARCHAR(100)) || ')') ELSE null END, chr(10)) as TRA_CDR3_WithFraction,

  group_concat(distinct CASE WHEN d.locus = 'TRB' THEN d.cdr3 ELSE null END, chr(10)) as TRB_CDR3,
  group_concat(distinct CASE WHEN d.locus = 'TRB' THEN d.cdr3 ELSE null END, ';') as TRB_CDR3_URL,
  group_concat(distinct CASE WHEN d.locus = 'TRB' THEN d.vGene ELSE null END, chr(10)) as TRB_V,
  group_concat(distinct CASE WHEN d.locus = 'TRB' THEN d.dGene ELSE null END, chr(10)) as TRB_D,
  group_concat(distinct CASE WHEN d.locus = 'TRB' THEN d.jGene ELSE null END, chr(10)) as TRB_J,
  group_concat(distinct CASE WHEN d.locus = 'TRB' THEN d.libraryId.species ELSE null END, chr(10)) as TRB_Species,
  sum(CASE WHEN d.locus = 'TRB' THEN 1 ELSE 0 END) as TRB_Count,
  group_concat(distinct CASE WHEN d.locus = 'TRB' THEN (d.cdr3 || ' (' || CAST(ROUND(d.fraction, 3) AS VARCHAR(100)) || ')') ELSE null END, chr(10)) as TRB_CDR3_WithFraction,

  group_concat(distinct CASE WHEN d.locus = 'TRD' THEN d.cdr3 ELSE null END, chr(10)) as TRD_CDR3,
  group_concat(distinct CASE WHEN d.locus = 'TRD' THEN d.cdr3 ELSE null END, ';') as TRD_CDR3_URL,
  group_concat(distinct CASE WHEN d.locus = 'TRD' THEN d.vGene ELSE null END, chr(10)) as TRD_V,
  group_concat(distinct CASE WHEN d.locus = 'TRD' THEN d.dGene ELSE null END, chr(10)) as TRD_D,
  group_concat(distinct CASE WHEN d.locus = 'TRD' THEN d.jGene ELSE null END, chr(10)) as TRD_J,
  group_concat(distinct CASE WHEN d.locus = 'TRD' THEN d.libraryId.species ELSE null END, chr(10)) as TRD_Species,
  sum(CASE WHEN d.locus = 'TRD' THEN 1 ELSE 0 END) as TRD_Count,
  group_concat(distinct CASE WHEN d.locus = 'TRD' THEN (d.cdr3 || ' (' || CAST(ROUND(d.fraction, 3) AS VARCHAR(100)) || ')') ELSE null END, chr(10)) as TRD_CDR3_WithFraction,

  group_concat(distinct CASE WHEN d.locus = 'TRG' THEN d.cdr3 ELSE null END, chr(10)) as TRG_CDR3,
  group_concat(distinct CASE WHEN d.locus = 'TRG' THEN d.cdr3 ELSE null END, ';') as TRG_CDR3_URL,
  group_concat(distinct CASE WHEN d.locus = 'TRG' THEN d.vGene ELSE null END, chr(10)) as TRG_V,
  group_concat(distinct CASE WHEN d.locus = 'TRG' THEN d.dGene ELSE null END, chr(10)) as TRG_D,
  group_concat(distinct CASE WHEN d.locus = 'TRG' THEN d.jGene ELSE null END, chr(10)) as TRG_J,
  group_concat(distinct CASE WHEN d.locus = 'TRG' THEN d.libraryId.species ELSE null END, chr(10)) as TRG_Species,
  sum(CASE WHEN d.locus = 'TRG' THEN 1 ELSE 0 END) as TRG_Count,
  group_concat(distinct CASE WHEN d.locus = 'TRG' THEN (d.cdr3 || ' (' || CAST(ROUND(d.fraction, 3) AS VARCHAR(100)) || ')') ELSE null END, chr(10)) as TRG_CDR3_WithFraction,

  d.run,
  d.folder,
  d.workbook

FROM Data d
WHERE (d.disabled IS NULL OR d.disabled = false)
GROUP BY d.subjectId, d.sampleName, d.analysisId, d.folder, d.run, d.workbook
