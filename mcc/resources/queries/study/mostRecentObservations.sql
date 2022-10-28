/*
 * Copyright (c) 2010-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
SELECT
    co.Id,
    co.date as date_of_observations,
    co.category,
    lower(replace(co.category, ' ', '_')) as fieldName,
    timestampdiff('SQL_TSI_DAY', co.date, now()) AS daysSinceObservation,
    --NOTE: we need to be careful in case duplicate records are entered on the same time
    GROUP_CONCAT(distinct co.observation) as observation

FROM study.clinical_observations co
    WHERE
        co.qcstate.publicdata = true and
        co.observation is not null AND
        co.category IN ('Medical History', 'Fertility Status', 'Infant History', 'Current Housing Status', 'Availability') AND
        co.date = (SELECT max(o.date) asMaxDate
                   FROM study.clinical_observations o
                   WHERE o.Id = co.Id AND
                         o.qcstate.publicdata = true AND
                         o.category IN ('Medical History', 'Fertility Status', 'Infant History', 'Current Housing Status', 'Availability') AND
                         o.observation is not null
                   )
GROUP BY co.id, co.category, co.date
