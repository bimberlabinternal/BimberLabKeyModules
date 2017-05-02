SELECT
  v.version,
  v.releaseDate,
  v.vcfId.dataFileUrl as vcfId,
  v.totalSubjects,
  v.totalVariants,
  v.dbSnpId

FROM mgap.variantCatalogReleases v