ALTER TABLE tcrdb.sorts ADD COLUMN plateId varchar(100);
ALTER TABLE tcrdb.sorts ADD COLUMN well varchar(100);

ALTER TABLE tcrdb.stims ADD COLUMN tubeNum int;
ALTER TABLE tcrdb.stims ADD COLUMN effector varchar(1000);
ALTER TABLE tcrdb.stims ADD COLUMN effectors int;
ALTER TABLE tcrdb.stims ADD COLUMN costim varchar(1000);
