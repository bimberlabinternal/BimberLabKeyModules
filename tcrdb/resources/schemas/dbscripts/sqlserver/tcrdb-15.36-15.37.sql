ALTER TABLE tcrdb.sorts ADD plateId varchar(100);
ALTER TABLE tcrdb.sorts ADD well varchar(100);

ALTER TABLE tcrdb.stims ADD tubeNum int;
ALTER TABLE tcrdb.stims ADD effector varchar(1000);
ALTER TABLE tcrdb.stims ADD effectors int;
ALTER TABLE tcrdb.stims ADD costim varchar(1000);