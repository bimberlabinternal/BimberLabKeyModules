SELECT
  d.Id.mccAlias.externalAlias as Id,
  d.Id as originalId,
  d.date,
  d.Id2MccAlias.externalAlias as Id2,
  d.Id2 as originalId2,
  d.kinship,
  d.relationship,
  d.objectid,
  d.container

FROM "/data/Colonies/SNPRC/".study.kinship d

UNION ALL

SELECT
    d.Id.mccAlias.externalAlias as Id,
    d.Id as originalId,
    d.date,
    d.Id2MccAlias.externalAlias as Id2,
    d.Id2 as originalId2,
    d.kinship,
    d.relationship,
    d.objectid,
    d.container

FROM "/data/Colonies/WNPRC/".study.kinship d

UNION ALL

SELECT
    d.Id.mccAlias.externalAlias as Id,
    d.Id as originalId,
    d.date,
    d.Id2MccAlias.externalAlias as Id2,
    d.Id2 as originalId2,
    d.kinship,
    d.relationship,
    d.objectid,
    d.container

FROM "/data/Colonies/UCSD/".study.kinship d

UNION ALL

SELECT
    d.Id.mccAlias.externalAlias as Id,
    d.Id as originalId,
    d.date,
    d.Id2MccAlias.externalAlias as Id2,
    d.Id2 as originalId2,
    d.kinship,
    d.relationship,
    d.objectid,
    d.container

FROM "/data/Colonies/Other/".study.kinship d