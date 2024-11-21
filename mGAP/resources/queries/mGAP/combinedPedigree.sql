SELECT
  s.Id as subjectname,
  s.gender,
  s.Id.parents.dam as dam,
  s.Id.parents.sire as sire,
  s.species,
  s.geographic_origin

FROM "/Internal/PMR/".study.demographics s

UNION ALL

SELECT
    d.subjectname,
    d.gender,
    d.dam,
    d.sire,
    d.species,
    null as geographic_origin

FROM mgap.demographics d
WHERE d.subjectname NOT IN (SELECT DISTINCT s.Id FROM "/Internal/PMR/".study.demographics s)