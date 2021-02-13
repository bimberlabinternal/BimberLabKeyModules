SELECT

  Id, date,
  cause,
  objectid, modified

FROM "/WNPRC/EHR/".study.weight
WHERE Id.demographics.species = 'Marmoset';