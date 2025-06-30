SELECT

MonkeyId as Id,
ID as sampleid,
SampleDate as date,
Tissue as sampleType,
CellCnt as quantity,

'Hansen/IDR' as dataSource

FROM bimber_data.ln_loc
WHERE SampleDate IS NOT NULL

UNION ALL

SELECT

Rh as Id,
ID as sampleid,
SampleDate as date,
Tissue as sampleType,
null as quantity,

'Hansen/IDR' as dataSource

FROM bimber_data.ult_loc
WHERE SampleDate IS NOT NULL