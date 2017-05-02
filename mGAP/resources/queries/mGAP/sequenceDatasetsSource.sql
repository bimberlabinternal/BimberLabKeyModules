SELECT
  r.mGAPAliases.externalAlias as mgapId,
  r.application as sequenceType,
  r.totalForwardReads as totalReads,
  r.sraRuns as sraAccession

FROM sequenceanalysis.sequence_readsets r
WHERE r.mGAPAliases.externalAlias IS NOT NULL AND r.sraRuns IS NOT NULL