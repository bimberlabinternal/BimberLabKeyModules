SELECT

Rh as subjectId,
PID0 as date,
'SIV Infection' as eventLabel,
'Hansen/IDR' as dataSource

FROM bimber_data.subjects
WHERE PID0 IS NOT NULL
AND Cohort NOT IN ('PC549', 'PC585', 'PC529') AND Cohort NOT LIKE 'W%'

UNION ALL

SELECT

Rh as subjectId,
D0 as date,
'Vaccination Start' as eventLabel,
'Hansen/IDR' as dataSource

FROM bimber_data.subjects
WHERE D0 IS NOT NULL
  AND Cohort NOT IN ('PC549', 'PC585', 'PC529') AND Cohort NOT LIKE 'W%'