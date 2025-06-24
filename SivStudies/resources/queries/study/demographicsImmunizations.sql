SELECT
  s.Id,
  group_concat(DISTINCT s.treatment, char(10)) as immunizations,
  group_concat(DISTINCT s.category, char(10)) as immunizationTypes,

FROM study.immunizations s
GROUP BY s.Id