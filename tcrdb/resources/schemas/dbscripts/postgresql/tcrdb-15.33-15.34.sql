ALTER TABLE tcrdb.mixcr_libraries ADD COLUMN libraryName varchar(1000);
ALTER TABLE tcrdb.mixcr_libraries DROP COLUMN local;
