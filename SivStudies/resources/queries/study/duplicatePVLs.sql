SELECT
    t.Id,
    t.date,
    t.sampleType,
    t.assayType,
    t.target,
    t.result,
    t.resultOORIndicator,
    t.dataSource,
    t.lsid,
    t.created,
    v.maxLsid,
    CASE WHEN t.lsid = v.maxLsid THEN TRUE ELSE FALSE END as isMostRecent

FROM study.viralLoads t
         INNER JOIN (
    SELECT v.Id, v.date, v.sampleType, v.assayType, max(v.lsid) as maxLsid
    FROM study.viralLoads v
    GROUP BY v.Id, v.date, v.sampleType, v.assayType
    HAVING count(*) > 1
) v ON (v.Id = t.Id AND v.date = t.date AND v.sampleType = t.sampleType AND v.assayType = t.assayType)
