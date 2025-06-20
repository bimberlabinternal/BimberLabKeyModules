SELECT

s1.subjectId as Id,
s1.date,

('Assay(s): ' || librarytype) as description

FROM (SELECT
    s.subjectId,
    coalesce(s.sampleDate, now()) as date,
    GROUP_CONCAT(DISTINCT s.librarytype, ', ') as librarytype

    FROM "/Internal/ColonyData".sequenceanalysis.sequence_readsets s
    GROUP BY s.subjectId, s.sampleDate
) s1