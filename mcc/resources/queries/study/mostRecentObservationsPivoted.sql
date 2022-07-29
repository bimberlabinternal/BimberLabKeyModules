/*
 * Copyright (c) 2010-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
SELECT
   t.Id,
   t.mostRecentObsDate,
   t.category,
   group_concat(distinct mostRecentObservation) as obs



FROM study.mostRecentObservations t
GROUP BY t.Id, t.mostRecentObsDate, t.category
PIVOT obs BY category