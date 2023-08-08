SELECT

    m.Id,
    GROUP_CONCAT(distinct m.originalId, ',') as originalIds

FROM mcc.aggregatedDemographics m

GROUP BY m.Id
HAVING COUNT(*) > 1