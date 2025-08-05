SELECT
t.Id,
t.date,
'SIV Infection' as category

FROM study.treatments t
WHERE t.category = 'SIV Infection'

GROUP BY t.Id, t.date
HAVING count(*) > 1