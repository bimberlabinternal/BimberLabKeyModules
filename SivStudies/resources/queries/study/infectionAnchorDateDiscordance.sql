SELECT
    t.Id,
    'SIV Infection' as category,
    t.date as treatmentTableDate,
    t2.date as subjectAnchorDatesTableDate

FROM study.treatments t
FULL JOIN studies.subjectAnchorDates t2 ON (
    t.category = 'SIV Infection' AND
    t2.subjectId = t.Id AND
    t2.eventLabel = 'SIV Infection'
)

WHERE t2.date != t.date OR
    t2.date IS NULL OR
    t.date IS NULL