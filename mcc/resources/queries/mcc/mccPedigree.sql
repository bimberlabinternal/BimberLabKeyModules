SELECT
    m.Id,
    m.dam,
    m.sire,
    m.species,
    m.gender,
    m.birth,
    m.death,
    m.calculated_status as status,
    m.colony,
    m.originalId,
    m.originalDam,
    m.originalSire,
    'Database' as category

FROM mcc.aggregatedDemographics m

-- These are sires listed, but not present in the main list:
UNION ALL
SELECT
    s.sire as Id,
    null as dam,
    null as sire,
    group_concat(distinct s.species) as species,
    'm' as gender,
    null as birth,
    null as death,
    'Unknown' as status,
    group_concat(distinct s.colony) as colony,
    group_concat(distinct s.originalSire) as originalId,
    null as originalDam,
    null as originalSire,
    'Added' as category
FROM mcc.aggregatedDemographics s
WHERE s.sire IS NOT NULL AND s.sire NOT IN (SELECT distinct ad.Id FROM mcc.aggregatedDemographics ad WHERE ad.Id IS NOT NULL)
GROUP BY s.sire

UNION ALL
SELECT
    s.dam as Id,
    null as dam,
    null as sire,
    group_concat(distinct s.species) as species,
    'f' as gender,
    null as birth,
    null as death,
    'Unknown' as status,
    group_concat(distinct s.colony) as colony,
    group_concat(distinct s.originalDam) as originalId,
    null as originalDam,
    null as originalSire,
    'Added' as category
FROM mcc.aggregatedDemographics s
WHERE s.dam IS NOT NULL AND s.dam NOT IN (SELECT distinct ad.Id FROM mcc.aggregatedDemographics ad WHERE ad.Id IS NOT NULL)
GROUP BY s.dam