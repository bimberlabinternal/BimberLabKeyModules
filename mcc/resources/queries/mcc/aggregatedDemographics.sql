SELECT
  d.Id.mccAlias.externalAlias as Id,
  d.Id as originalId,
  d.date,
  d.species,
  d.gender,
  d.birth,
  d.death,
  d.colony,
  d.source,
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
  d.Id.mostRecentDeparture.mostRecentDeparture,
  o.availability,
  o.current_housing_status,
  o.infant_history,
  o.fertility_status,
  o.medical_history,
  null as usage_current,
  null as usage_future,
  null as breeding_partner_id,
  o.date_of_observations,
  d.container

FROM "/data/Colonies/SNPRC/".study.demographics d
LEFT JOIN (SELECT
                   o.Id,
                   o.date_of_observations,
                   o."availability::observation" as availability,
                   o."current_housing_status::observation" as current_housing_status,
                   o."infant_history::observation" as infant_history,
                   o."fertility_status::observation" as fertility_status,
                   o."medical_history::observation" as medical_history
    FROM "/data/Colonies/SNPRC/".study.mostRecentObservationsPivoted o
) o ON (o.Id = d.Id)
WHERE (d.excludeFromCensus IS NULL or d.excludeFromCensus = false) and d.calculated_status NOT IN ('Other')

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
  d.source,
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
  d.Id.mostRecentDeparture.mostRecentDeparture,
  o.availability,
  o.current_housing_status,
  o.infant_history,
  o.fertility_status,
  o.medical_history,
  null as usage_current,
  null as usage_future,
  null as breeding_partner_id,
  o.date_of_observations,
  d.container

FROM "/data/Colonies/WNPRC/".study.demographics d
         LEFT JOIN (SELECT
                        o.Id,
                        o.date_of_observations,
                        o."availability::observation" as availability,
                        o."current_housing_status::observation" as current_housing_status,
                        o."infant_history::observation" as infant_history,
                        o."fertility_status::observation" as fertility_status,
                        o."medical_history::observation" as medical_history,
                    FROM "/data/Colonies/WNPRC/".study.mostRecentObservationsPivoted o
) o ON (o.Id = d.Id)
WHERE (d.excludeFromCensus IS NULL or d.excludeFromCensus = false)

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
    d.source,
    d.damMccAlias.externalAlias as dam,
    d.sireMccAlias.externalAlias as sire,
    d.dam as originalDam,
    d.sire as originalSire,
    d.Id.mostRecentWeight.mostRecentWeight as mostRecentWeight,
    d.objectid,
    d.calculated_status,
    d.u24_status,
    d.Id.mostRecentDeparture.mostRecentDeparture,
    o.availability,
    o.current_housing_status,
    o.infant_history,
    o.fertility_status,
    o.medical_history,
    o.usage_current,
    o.usage_future,
    o.breeding_partner_id,
    o.date_of_observations,
    d.container

FROM "/data/Colonies/UCSD/".study.demographics d
         LEFT JOIN (SELECT
                        o.Id,
                        o.date_of_observations,
                        o."availability::observation" as availability,
                        o."current_housing_status::observation" as current_housing_status,
                        o."infant_history::observation" as infant_history,
                        o."fertility_status::observation" as fertility_status,
                        o."medical_history::observation" as medical_history,
                        o."usage_current::observation" as usage_current,
                        o."usage_current::observation" as usage_future,
                        o."breeding_partner_id::observation" as breeding_partner_id
                    FROM "/data/Colonies/UCSD/".study.mostRecentObservationsPivoted o
) o ON (o.Id = d.Id)
WHERE (d.excludeFromCensus IS NULL or d.excludeFromCensus = false)

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
    d.source,
    d.damMccAlias.externalAlias as dam,
    d.sireMccAlias.externalAlias as sire,
    d.dam as originalDam,
    d.sire as originalSire,
    d.Id.mostRecentWeight.mostRecentWeight as mostRecentWeight,
    d.objectid,
    d.calculated_status,
    d.u24_status,
    d.Id.mostRecentDeparture.mostRecentDeparture,
    o.availability,
    o.current_housing_status,
    o.infant_history,
    o.fertility_status,
    o.medical_history,
    o.usage_current,
    o.usage_future,
    o.breeding_partner_id,
    o.date_of_observations,
    d.container

FROM "/data/Colonies/Other/".study.demographics d
         LEFT JOIN (SELECT
                        o.Id,
                        o.date_of_observations,
                        o."availability::observation" as availability,
                        o."current_housing_status::observation" as current_housing_status,
                        o."infant_history::observation" as infant_history,
                        o."fertility_status::observation" as fertility_status,
                        o."medical_history::observation" as medical_history,
                        o."usage_current::observation" as usage_current,
                        o."usage_current::observation" as usage_future,
                        o."breeding_partner_id::observation" as breeding_partner_id
                    FROM "/data/Colonies/Other/".study.mostRecentObservationsPivoted o
) o ON (o.Id = d.Id)
WHERE (d.excludeFromCensus IS NULL or d.excludeFromCensus = false)