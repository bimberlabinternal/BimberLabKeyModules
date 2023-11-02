SELECT

m.subjectname,
GROUP_CONCAT(distinct m.externalAlias, ',') as mccIds,
count(distinct m.externalAlias) as numMccIds,
GROUP_CONCAT(distinct m.container.name, ',') as containerNames

FROM mcc.animalMapping m

GROUP BY m.subjectname
HAVING COUNT(*) > 1