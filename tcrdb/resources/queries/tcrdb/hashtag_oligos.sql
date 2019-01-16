SELECT
  b.tag_name,
  b.sequence,
  b.group_name

FROM sequenceanalysis.barcodes b
WHERE group_name = '5p-HTOs'