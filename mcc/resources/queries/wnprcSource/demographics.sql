SELECT

Id, date, gender, geographic_origin, birth, death, species, objectid, modified

FROM "/WNPRC/EHR/".study.demographics
WHERE species = 'Marmoset';