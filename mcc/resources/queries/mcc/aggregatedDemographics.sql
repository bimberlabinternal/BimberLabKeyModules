SELECT
  d.Id,
  d.date,
  d.species,
  d.gender,
  d.birth,
  d.death,
  d.colony,
  d.dam,
  d.sire,
  d.Id.mostRecentWeight.mostRecentWeight as mostRecentWeight,
  d.objectid

FROM "/data/Colonies/SNPRC/".study.demographics d

UNION ALL

SELECT
  d.Id,
  d.date,
  d.species,
  d.gender,
  d.birth,
  d.death,
  d.colony,
  d.dam,
  d.sire,
  d.Id.mostRecentWeight.mostRecentWeight as mostRecentWeight,
  d.objectid

FROM "/data/Colonies/WNPRC/".study.demographics d