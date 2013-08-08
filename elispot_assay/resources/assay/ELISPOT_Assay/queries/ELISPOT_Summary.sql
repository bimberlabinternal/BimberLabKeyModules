SELECT
d.subjectid,
d.date,
d.category,
d.peptide,
avg(d.spots) as avgSpots,
avg(d.spotsAboveBackground) as avgSpotsAboveBackground,
avg(d.pValue) as pValue,
count(*) as replicates,
group_concat(DISTINCT d.qual_result) as result,
stddev(spotsAboveBackground) as stdDeviation,
group_concat(DISTINCT comment, chr(10)) as comments,
d.run,
d.workbook

FROM data d
GROUP BY d.run, d.subjectid, d.date, d.category, d.workbook, d.peptide