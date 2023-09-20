SELECT

    m.originalDam as Id,
    GROUP_CONCAT(distinct m.dam, ',') as mccIds,
    'Dam' as category,
    GROUP_CONCAT(distinct m.colony, ',') as colonies

FROM mcc.aggregatedDemographics m
WHERE m.dam IS NOT NULL
GROUP BY m.originalDam
HAVING COUNT(distinct m.dam) > 1

UNION ALL

SELECT

    m.originalSire as Id,
    GROUP_CONCAT(distinct m.sire, ',') as mccIds,
    'Sire' as category,
    GROUP_CONCAT(distinct m.colony, ',') as colonies

FROM mcc.aggregatedDemographics m
WHERE m.sire IS NOT NULL
GROUP BY m.originalSire
HAVING COUNT(distinct m.sire) > 1