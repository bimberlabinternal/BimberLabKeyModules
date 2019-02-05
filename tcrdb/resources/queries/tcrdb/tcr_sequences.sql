SELECT
rowid,
name,
species

FROM sequenceanalysis.ref_nt_sequences
WHERE name like 'TR%' AND datedisabled IS NULL