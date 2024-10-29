SELECT
  m.externalAlias as subjectName,
  coalesce(s.gender, d.gender) as gender,
  coalesce(s.species, d.species) as species,
  coalesce(s.geographic_origin, d.geographic_origin) as geographic_origin,
--        TODO: geographic origin score

  CASE
    WHEN d.center IS NOT NULL THEN d.center
    WHEN s.Id IS NOT NULL THEN 'ONPRC'
    ELSE NULL END as center,
  d.status as status,
  m.subjectname as originalId

FROM mgap.animalMapping m
LEFT JOIN "/Internal/PMR/".study.demographics s ON (m.subjectname = s.Id)
LEFT JOIN mgap.demographics d ON (m.subjectname = d.subjectname)
WHERE (s.Id IS NOT NULL OR d.subjectname IS NOT NULL)