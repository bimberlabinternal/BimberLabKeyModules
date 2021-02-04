SELECT

Id, date, gender, geographic_origin, birth, death, species, objectid

FROM "/WNPRC/EHR/".study.demographics
WHERE species = 'Marmoset';