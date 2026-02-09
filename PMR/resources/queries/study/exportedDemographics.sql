SELECT
    d.Id,
    d.date,
    d.gender,
    d.species,
    d.geographic_origin,
    d.birth,
    d.death,
    d.calculated_status,
    d.Id.parents.dam as dam,
    d.Id.parents.sire as sire

FROM study.demographics d