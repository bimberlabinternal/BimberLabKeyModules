SELECT
  s.Id,
  count(s.Id) as totalProjects,
  group_concat(DISTINCT s.study, char(10)) as allStudies,
  group_concat(DISTINCT s.category, char(10)) as categories,
  group_concat(DISTINCT s.subgroup, char(10)) as subgroups,

  GROUP_CONCAT(distinct CASE WHEN s.category = 'RhCMV-Vaccines' THEN 'Yes' ELSE null END, char(10)) as rhCmvVaccines,
  GROUP_CONCAT(distinct CASE WHEN s.category = 'SIV/ART' THEN 'Yes' ELSE null END, char(10)) as sivArt
FROM study.assignment s
GROUP BY s.Id