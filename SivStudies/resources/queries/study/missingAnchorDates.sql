SELECT
    dc.Id as SubjectId,
    dc.infectionDate as date,
    'SIV Infection' as eventLabel

FROM study.demographicsChallengeAndArt dc
LEFT JOIN studies.subjectAnchorDates ad ON (dc.Id = ad.SubjectId AND dc.infectionDate = ad.date AND ad.eventLabel = 'SIV Infection')
WHERE ad.rowid IS NULL AND dc.infectionDate IS NOT NULL

UNION ALL

SELECT
    dc.Id as SubjectId,
    dc.artInitiationDate as date,
    'ART Initiation' as eventLabel

FROM study.demographicsChallengeAndArt dc
         LEFT JOIN studies.subjectAnchorDates ad ON (dc.Id = ad.SubjectId AND dc.artInitiationDate = ad.date AND ad.eventLabel = 'ART Initiation')
WHERE ad.rowid IS NULL AND dc.artInitiationDate IS NOT NULL

UNION ALL

SELECT
    dc.Id as SubjectId,
    dc.artReleaseDate as date,
    'ART Release' as eventLabel

FROM study.demographicsChallengeAndArt dc
    LEFT JOIN studies.subjectAnchorDates ad ON (dc.Id = ad.SubjectId AND dc.artReleaseDate = ad.date AND ad.eventLabel = 'ART Release')
WHERE ad.rowid IS NULL AND dc.artReleaseDate IS NOT NULL