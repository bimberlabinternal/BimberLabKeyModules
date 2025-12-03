SELECT
    t.Id,
    group_concat(DISTINCT (t.treatment || ' (' || COALESCE(t.timePostSivChallenge.timePostInfection, 'Unk DPI') || ')'), char(10)) as allInterventions,
    min(t.date) as firstInterventionDate,
    min(t.timePostSivChallenge.daysPostInfection) as firstInterventionDPI,
    min(t.timePostSivChallenge.weeksPostInfection) as firstInterventionWPI,
    min(t.timePostSivChallenge.weeksPostInfection) - min(t.sivART.artReleaseWPI) as firstInterventionPostArtReleaseWeeks
FROM study.treatments t
WHERE t.category = 'Intervention'
GROUP BY t.Id