SELECT
  d.Id.mccAlias.externalAlias as Id,
  d.Id as originalId,
  d.date,
  d.species,
  d.gender,
  d.birth,
  d.death,
  d.colony,
  d.damMccAlias.externalAlias as dam,
  d.sireMccAlias.externalAlias as sire,
  d.dam as originalDam,
  d.sire as originalSire,
  d.Id.mostRecentWeight.mostRecentWeight as mostRecentWeight,
  d.objectid,
  d.calculated_status,
  CASE
    WHEN d.calculated_status = 'Alive' AND (SELECT COUNT(f.flag.value) as total FROM "/data/Colonies/SNPRC/".study.flags f WHERE f.Id = d.Id AND f.isActive = true) > 0 THEN true
    ELSE false
  END as u24_status,
  d.container

FROM "/data/Colonies/SNPRC/".study.demographics d
LEFT JOIN (SELECT
    o.Id,
    o.date_of_observations,
    o.availability,
    o.current_housing_status,
    o.infant_history,
    o.fertility_status,
    o.medical_history,
    FROM "/data/Colonies/SNPRC/".study.mostRecentObservationsPivoted o
) o ON (o.Id = d.Id)

UNION ALL

SELECT
  d.Id.mccAlias.externalAlias as Id,
  d.Id as originalId,
  d.date,
  d.species,
  d.gender,
  d.birth,
  d.death,
  d.colony,
  d.damMccAlias.externalAlias as dam,
  d.sireMccAlias.externalAlias as sire,
  d.dam as originalDam,
  d.sire as originalSire,
  d.Id.mostRecentWeight.mostRecentWeight as mostRecentWeight,
  d.objectid,
  d.calculated_status,
  CASE
    WHEN d.calculated_status = 'Alive' AND (SELECT COUNT(f.flag.value) as total FROM "/data/Colonies/WNPRC/".study.flags f WHERE f.Id = d.Id AND f.isActive = true) > 0 THEN true
    ELSE false
  END as u24_status,
  d.container

FROM "/data/Colonies/WNPRC/".study.demographics d
         LEFT JOIN (SELECT
                        o.Id,
                        o.date_of_observations,
                        o.availability,
                        o.current_housing_status,
                        o.infant_history,
                        o.fertility_status,
                        o.medical_history,
                    FROM "/data/Colonies/WNPRC/".study.mostRecentObservationsPivoted o
) o ON (o.Id = d.Id)

UNION ALL

SELECT
    d.Id.mccAlias.externalAlias as Id,
    d.Id as originalId,
    d.date,
    d.species,
    d.gender,
    d.birth,
    d.death,
    'U NEB' as colony,
    d.damMccAlias.externalAlias as dam,
    d.sireMccAlias.externalAlias as sire,
    d.dam as originalDam,
    d.sire as originalSire,
    d.Id.mostRecentWeight.mostRecentWeight as mostRecentWeight,
    d.objectid,
    d.calculated_status,
    false as u24_status,
    d.container

FROM "/data/Colonies/UNO/".study.demographics d
         LEFT JOIN (SELECT
                        o.Id,
                        o.date_of_observations,
                        o.availability,
                        o.current_housing_status,
                        o.infant_history,
                        o.fertility_status,
                        o.medical_history,
                    FROM "/data/Colonies/UNO/".study.mostRecentObservationsPivoted o
) o ON (o.Id = d.Id)

UNION ALL

SELECT
    d.Id.mccAlias.externalAlias as Id,
    d.Id as originalId,
    d.date,
    d.species,
    d.gender,
    d.birth,
    d.death,
    d.colony,
    d.damMccAlias.externalAlias as dam,
    d.sireMccAlias.externalAlias as sire,
    d.dam as originalDam,
    d.sire as originalSire,
    d.Id.mostRecentWeight.mostRecentWeight as mostRecentWeight,
    d.objectid,
    d.calculated_status,
    d.u24_status,
    d.container

FROM "/data/Colonies/UCSD/".study.demographics d
         LEFT JOIN (SELECT
                        o.Id,
                        o.date_of_observations,
                        o.availability,
                        o.current_housing_status,
                        o.infant_history,
                        o.fertility_status,
                        o.medical_history,
                    FROM "/data/Colonies/UCSD/".study.mostRecentObservationsPivoted o
) o ON (o.Id = d.Id)

UNION ALL

SELECT
    d.Id.mccAlias.externalAlias as Id,
    d.Id as originalId,
    d.date,
    d.species,
    d.gender,
    d.birth,
    d.death,
    d.colony,
    d.damMccAlias.externalAlias as dam,
    d.sireMccAlias.externalAlias as sire,
    d.dam as originalDam,
    d.sire as originalSire,
    d.Id.mostRecentWeight.mostRecentWeight as mostRecentWeight,
    d.objectid,
    d.calculated_status,
    d.u24_status,
    d.container

FROM "/data/Colonies/Other/".study.demographics d
         LEFT JOIN (SELECT
                        o.Id,
                        o.date_of_observations,
                        o.availability,
                        o.current_housing_status,
                        o.infant_history,
                        o.fertility_status,
                        o.medical_history,
                    FROM "/data/Colonies/Other/".study.mostRecentObservationsPivoted o
) o ON (o.Id = d.Id)