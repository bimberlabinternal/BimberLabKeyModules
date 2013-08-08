/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
--this query provides an overview of the MHC SSP results
SELECT
  s.subjectId,
  s.date,
  s.population as population,
  s.run.rowid as run,
  max(s.result) as Result,

FROM Data s

GROUP BY s.subjectId, s.date, s.population, s.run.rowid
PIVOT result BY population