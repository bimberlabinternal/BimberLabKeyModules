SELECT
    coalesce(t.Id, t2.subjectId) as Id,
    'SIV Infection' as category,
    t.date as treatmentTableDate,
    t2.date as subjectAnchorDatesTableDate

FROM study.treatments t
FULL JOIN studies.subjectAnchorDates t2 ON (
    t.category = t2.eventLabel AND
    t2.subjectId = t.Id
)

WHERE (t2.date != t.date OR
    t2.date IS NULL OR
    t.date IS NULL) AND COALESCE(t.category, t2.eventLabel) = 'SIV Infection'