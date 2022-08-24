SELECT

m.subjectname,
GROUP_CONCAT(distinct m.externalAlias, ',') as mccIds

FROM mcc.animalMapping m

GROUP BY m.subjectname
HAVING COUNT(*) > 1