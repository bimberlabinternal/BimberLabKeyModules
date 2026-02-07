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
  m.subjectname as originalId,
  p1.externalAlias as sire,
  coalesce(s.sire, d.sire) as originalSire,
  p2.externalAlias as dam,
  coalesce(s.dam, d.dam) as originalDam,

FROM mgap.animalMapping m
LEFT JOIN PMR_Data.exportedDemographics s ON (m.subjectname = s.Id)
LEFT JOIN mgap.demographics d ON (m.subjectname = d.subjectname)
LEFT JOIN mgap.animalMapping p1 ON (p1.subjectname = coalesce(s.sire, d.sire))
LEFT JOIN mgap.animalMapping p2 ON (p2.subjectname = coalesce(s.dam, d.dam))
WHERE (s.Id IS NOT NULL OR d.subjectname IS NOT NULL)