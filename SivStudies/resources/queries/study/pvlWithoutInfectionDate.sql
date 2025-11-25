SELECT
    vl.Id,
    max(vl.result) as maxViralLoad

FROM study.viralloads vl
WHERE
    vl.result IS NOT NULL AND
    ((vl.lod is not NULL AND vl.result > vl.lod) OR (vl.lod IS NULL AND vl.result > 50)) AND
    vl.timePostSivChallenge.infectionDate IS NULL
GROUP BY vl.Id