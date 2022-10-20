/*
 * Copyright (c) 2010-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
SELECT
   t.Id,
   t.date_of_observations,
   t.fieldName,
   group_concat(distinct observation) as observation



FROM study.mostRecentObservations t
GROUP BY t.Id, t.date_of_observations, t.fieldName
PIVOT observation BY fieldName