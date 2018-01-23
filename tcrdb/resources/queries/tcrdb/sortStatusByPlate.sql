SELECT
  t2.sortPlateId,
  t2.workbooks,
  t2.totalSorts,
  t2.totalBulkSorts,
  t2.totalSingleCells,
  CASE WHEN t2.totalSorts - t2.totalLibraries = 0 THEN true ELSE false END as isComplete,
  CASE
    WHEN (t2.totalBulkSorts > 0 AND t2.totalSingleCells > 0) THEN 'MIXED'
    WHEN (t2.totalBulkSorts > 0) THEN 'BULK'
    WHEN (t2.totalSingleCells > 0) THEN 'SINGLE'
  END as sortType
FROM (
SELECT
  t.plateId as sortPlateId,
  t.totalSorts,
  t.totalBulkSorts,
  t.totalSingleCells,
  t.workbooks,
  (SELECT count(*) as expr  from tcrdb.cdnas c1 WHERE c1.sortId.plateId = t.plateId) as totalLibraries,
  (SELECT count(*) as expr  from tcrdb.cdnas c2 WHERE c2.sortId.plateId = t.plateId AND c2.hasReadsetWithData = true) as totalLibrariesWithData,
  (SELECT group_concat(distinct c3.plateId, chr(10)) as expr from tcrdb.cdnas c3 WHERE c3.sortId.plateId = t.plateId) as libraryPlates

FROM (
  SELECT
    s.plateId,
    count(*) AS totalSorts,
    SUM(CASE WHEN s.cells > 1 THEN 1 ELSE 0 END) AS totalBulkSorts,
    SUM(CASE WHEN s.cells = 1 THEN 1 ELSE 0 END) AS totalSingleCells,
    group_concat(distinct s.workbook.workbookId) as workbooks

  FROM tcrdb.sorts s
  GROUP BY s.plateId

) t
) t2