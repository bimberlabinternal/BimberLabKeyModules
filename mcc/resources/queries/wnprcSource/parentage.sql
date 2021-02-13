SELECT

  Id,
  date,
  sire as parent,
  'Sire' as relationship,
  'Observed' as method,
  cast(objectid as varchar) || '-Sire' as objectid,
  modified

FROM "/WNPRC/EHR/".study.demographics
WHERE species = 'Marmoset' and sire is not null

UNION ALL

SELECT

  Id,
  date,
  sire as parent,
  'Dam' as relationship,
  'Observed' as method,
  cast(objectid as varchar) || '-Dam' as objectid,
  modified

FROM "/WNPRC/EHR/".study.demographics
WHERE species = 'Marmoset' and dam is not null