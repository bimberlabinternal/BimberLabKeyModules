SELECT

s1.subjectId as Id,
s1.date,

('Assay(s): ' || assayType || char(10) ||
'Tissue(s): ' || tissue || char(10) ||
'Stims(s): ' || stims || char(10)) as description,
'Bimber Lab' as dataSource,
 NULL as accessions

FROM (SELECT
    s.subjectId,
    s.sampleDate as date,
    GROUP_CONCAT(DISTINCT s.tissue, ', ') as tissue,
    GROUP_CONCAT(DISTINCT s.assayType, ', ') as assayType,
    GROUP_CONCAT(DISTINCT s.stim, ', ') as stims

    FROM "/Labs/Bimber".singlecell.samples s
    GROUP BY s.subjectId, s.sampleDate
) s1

UNION ALL

SELECT

    s1.subjectId as Id,
    s1.date,

    ('Assay(s): ' || application || char(10) ||
    'Types(s): ' || coalesce(librarytype, 'N/A') || char(10) ||
    'Sample Types(s): ' || coalesce(sampletype, 'N/A')
    ) as description,
    'Bimber Lab' as dataSource,
    s1.accessions

FROM (SELECT
      s.subjectId,
      s.sampleDate as date,
      s.application,
      s.sampletype,
      s.libraryType,
      group_concat(DISTINCT s.sraRuns) as accessions

      FROM "/Labs/Bimber".sequenceanalysis.sequence_readsets s
      WHERE s.subjectId IS NOT NULL AND s.application = 'RNA-seq' AND s.sampleDate IS NOT NULL
      AND librarytype != 'SMART-seq2'
      GROUP BY s.subjectId, s.sampleDate, s.sampletype, s.application, s.libraryType
 ) s1