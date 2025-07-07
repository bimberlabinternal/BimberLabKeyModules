SELECT
  t.Id,
  group_concat(DISTINCT CASE
      WHEN t.category = 'SIV Infection' THEN (cast(month(t.date) as varchar) || '-' || cast(dayofmonth(t.date) as varchar) || '-' || cast(year(t.date) as varchar) || ' (' || t.treatment || ')')
      ELSE NULL
  END, char(10)) as allInfections,
  group_concat(DISTINCT CASE
      WHEN t.category = 'ART' THEN (cast(month(t.date) as varchar) || '-' || cast(dayofmonth(t.date) as varchar) || '-' || cast(year(t.date) as varchar) || ' (' || t.treatment || ')')
      ELSE NULL
  END, char(10)) as allART,

FROM study.treatments t
GROUP BY t.Id