SELECT
  v.genomeId.name as genomeId,


FROM mgap.variantCatalogReleases v
JOIN sequenceanalysis.reference_libraries l on (v.genomeId = l.rowid)