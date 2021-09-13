SELECT
  am.externalAlias as mgapId,
  r.application as sequenceType,
  r.totalForwardReads as totalReads,
  r.sraRuns as sraAccession,
  r.subjectId as originalId,
  (SELECT s.center FROM mgap.subjectsource s WHERE s.subjectName = am.externalAlias) as center

FROM sequenceanalysis.sequence_readsets r
JOIN mgap.animalMapping am ON (r.subjectId = am.subjectname)
WHERE am.externalAlias IS NOT NULL AND r.sraRuns IS NOT NULL
AND (r.status IS NULL OR r.status NOT IN ('Duplicate', 'Failed'))