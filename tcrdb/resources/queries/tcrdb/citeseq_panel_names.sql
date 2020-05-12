SELECT
  distinct name,
  count(*) as totalMarkers

FROM tcrdb.citeseq_panels
GROUP BY name