/*
 * Copyright (c) 2010-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
SELECT

    co.id,
    co.category,
    co.mostRecentObsDate,
    timestampdiff('SQL_TSI_DAY', co.MostRecentObsDate, now()) AS daysSinceObservation,

    --NOTE: we need to be careful in case duplicate records are entered on the same time
    (SELECT GROUP_CONCAT(distinct o2.observation) AS _expr
         FROM study.clinical_observations o2
    WHERE co.id = o2.id AND co.category = o2.category AND co.MostRecentObsDate=o2.date) AS mostRecentObservation

FROM (
    SELECT
    co.Id,
    co.category,
    max(co.date) AS MostRecentObsDate

    FROM study.clinical_observations co
    WHERE co.qcstate.publicdata = true and co.observation is not null
    GROUP BY co.id, co.category
    ) co
