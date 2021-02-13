SELECT

  Id, date,
  weight,
  objectid, modified

FROM "/WNPRC/EHR/".study.weight
WHERE Id.demographics.species = 'Marmoset';