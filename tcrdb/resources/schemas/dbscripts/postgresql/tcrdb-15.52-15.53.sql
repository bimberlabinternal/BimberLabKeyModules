drop table tcrdb.stims;
drop table tcrdb.sorts;
drop table tcrdb.cdnas;
drop table tcrdb.peptides;
drop table tcrdb.plate_processing;
drop table tcrdb.citeseq_panels;
drop table tcrdb.citeseq_antibodies;

ALTER TABLE tcrdb.clone_responses DROP COLUMN experiment;
ALTER TABLE tcrdb.clone_responses DROP COLUMN cloneName;
ALTER TABLE tcrdb.clone_responses DROP COLUMN date;
ALTER TABLE tcrdb.clone_responses DROP COLUMN cellBackground;
ALTER TABLE tcrdb.clone_responses DROP COLUMN numEffectors;
ALTER TABLE tcrdb.clone_responses DROP COLUMN pctTransduction;
ALTER TABLE tcrdb.clone_responses DROP COLUMN costim;
ALTER TABLE tcrdb.clone_responses DROP COLUMN antigen;
ALTER TABLE tcrdb.clone_responses DROP COLUMN stim;

ALTER TABLE tcrdb.clone_responses ADD COLUMN cdna_id int;
ALTER TABLE tcrdb.clone_responses ADD COLUMN nostimid int;
ALTER TABLE tcrdb.clone_responses ADD COLUMN chain varchar(100);
ALTER TABLE tcrdb.clone_responses ADD COLUMN clonotype varchar(1000);

