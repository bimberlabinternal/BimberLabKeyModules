/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
SELECT
  s.analysisId,
  s.sampleName,
  s.cdr3,
  s.run.rowid as run,
  sum(s."count") as totalClones,

FROM Data s

GROUP BY s.analysisId, s.sampleName, s.cdr3, s.run.rowid
PIVOT totalClones BY sampleName
