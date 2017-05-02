SELECT
  m.externalAlias as subjectName,
  s.gender,
  s.species,
  s.geographic_origin

FROM mgap.animalMapping m
JOIN laboratory.subjects s ON (m.subjectname = s.subjectname)