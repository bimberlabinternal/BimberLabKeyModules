SELECT

Rh as Id,
cohortStart as date,

CASE
    WHEN contprog = 'C' THEN 'Controller'
    WHEN contprog = 'P' THEN 'Progressor'
    ELSE contprog
END as outcome,

'Hansen/IDR' as dataSource

FROM bimber_data.subjects
WHERE contprog IS NOT NULL AND contprog != ''
AND Cohort NOT IN ('PC549', 'PC585', 'PC529') AND Cohort NOT LIKE 'W%'