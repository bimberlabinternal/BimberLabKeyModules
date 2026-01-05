SELECT
t.Id,
t.date,
t.sampleType,
t.assayType,
t.target,
t.result,
t.dataSource

FROM study.viralLoads t

WHERE t.lsid IN (SELECT lsid FROM study.viralLoads v GROUP BY v.Id, v.date, v.sampleType, v.assayType HAVING count(*) > 1)