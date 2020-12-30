IF EXISTS (SELECT * FROM sys.schemas WHERE name = 'singlecell')
BEGIN

--Insert data
SET IDENTITY_INSERT singlecell.samples ON;
INSERT INTO singlecell.samples
(subjectId, sampledate, tissue, celltype, stim, assaytype, comment, status, lsid, container, created, createdby, modified, modifiedby, rowid)
SELECT
animalId, date, tissue, effector, stim, treatment, comment, status, lsid, container, created, createdby, modified, modifiedby, rowid
FROM tcrdb.stims;
SET IDENTITY_INSERT singlecell.samples OFF;

SET IDENTITY_INSERT singlecell.sorts ON;
INSERT INTO singlecell.sorts
(sampleId, population, replicate, cells, plateId, well, buffer, hto, comment, lsid, container, created, createdby, modified, modifiedby, rowid)
SELECT
stimid, population, replicate, cells, plateId, well, buffer, hto, comment, lsid, container, created, createdby, modified, modifiedby, rowid
FROM tcrdb.sorts;
SET IDENTITY_INSERT singlecell.sorts OFF;

SET IDENTITY_INSERT singlecell.cdna_libraries ON;
INSERT INTO singlecell.cdna_libraries
(rowid, sortid, chemistry, concentration, plateId, well, readsetid, tcrreadsetid, hashingReadsetId, citeseqReadsetId, citeseqPanel, comment, status, lsid, container, created, createdby, modified, modifiedby)
SELECT
rowid, sortid, chemistry, concentration, plateId, well, readsetid, enrichedreadsetid, hashingReadsetId, citeseqReadsetId, citeseqPanel, comment, status, lsid, container, created, createdby, modified, modifiedby
FROM tcrdb.cdnas;
SET IDENTITY_INSERT singlecell.cdna_libraries OFF;

INSERT INTO singlecell.stim_types
(name, category, type, container, created, createdby, modified, modifiedby)
SELECT
stim, category, type, container, created, createdby, modified, modifiedby
FROM tcrdb.peptides;

INSERT INTO singlecell.citeseq_panels
(name, antibody, markerLabel, container, created, createdby, modified, modifiedby)
SELECT
name, antibody, markerLabel, container, created, createdby, modified, modifiedby
FROM tcrdb.citeseq_panels;


INSERT INTO singlecell.citeseq_antibodies
(antibodyName, markerName, markerLabel, cloneName, vendor, productId, barcodeName, adaptersequence, container, created, createdby, modified, modifiedby)
SELECT
antibodyName, markerName, markerLabel, cloneName, vendor, productId, barcodeName, adaptersequence, container, created, createdby, modified, modifiedby
FROM tcrdb.citeseq_antibodies;

END