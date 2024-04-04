SELECT
  s.subjectname,
  s.gender,
  s.mother as dam,
  s.father as sire,
  s.species,
  s.geographic_origin,

FROM laboratory.subjects s

UNION ALL

SELECT
    d.subjectname,
    d.gender,
    d.dam,
    d.sire,
    d.species,
    null as geographic_origin

FROM mgap.demographics d
WHERE d.subjectname NOT IN (SELECT DISTINCT s.subjectname FROM laboratory.subjects s)