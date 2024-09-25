SELECT
t.*,
CAST((CAST(totalClones AS DOUBLE) / t.totalClonesWithTRB_CDR3) as double) as percent
FROM (
SELECT
r.subjectId,
count(DISTINCT r.sampleName) as totalSamples,
r.date,
r.TRB_CDR3,
r.TRB_CDR3_WithFraction,
count(*) as totalClones,
GROUP_CONCAT(DISTINCT r.TRA_CDR3, chr(10)) as TRA_CDR3,
GROUP_CONCAT(DISTINCT r.TRA_CDR3, ';') as TRA_CDR3_URL,
r.folder,
r.workbook,
(SELECT count(*) as expr FROM ResultsBySample r2 WHERE r2.subjectId = r.subjectId AND isequal(r.date, r2.date) AND r2.folder = r.folder AND r2.TRB_CDR3 IS NOT NULL) as totalClonesWithTRB_CDR3,
(SELECT count(*) as expr FROM ResultsBySample r3 WHERE r3.subjectId = r.subjectId AND isequal(r.date, r3.date) AND r3.folder = r.folder ) as totalClonesFromSubject,

FROM ResultsBySample r
WHERE r.TRB_CDR3 is not null

GROUP BY r.subjectId, r.date, r.TRB_CDR3, r.TRB_CDR3_WithFraction, r.folder, r.workbook

) t