ALTER TABLE tcrdb.sorts ADD hto varchar(100);
ALTER TABLE tcrdb.cdnas ADD hashingReadsetId int;

ALTER TABLE tcrdb.sorts DROP COLUMN enrichedReadsetId;
ALTER TABLE tcrdb.sorts DROP COLUMN readsetId;

ALTER TABLE tcrdb.cdnas ADD cdnaConc double precision;
ALTER TABLE tcrdb.cdnas ADD enrichedConc double precision;
