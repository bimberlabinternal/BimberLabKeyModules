SELECT

m.subjectname,
GROUP_CONCAT(distinct m.externalAlias, ',') as mgapIds

FROM mgap.animalMapping m

GROUP BY m.subjectname
HAVING COUNT(*) > 1