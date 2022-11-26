ALTER TABLE labpurchasing.purchases ADD emailOnArrival bit DEFAULT 1;
ALTER TABLE labpurchasing.purchases ADD excludeFromRefItems bit DEFAULT 0;

ALTER TABLE labpurchasing.purchases DROP COLUMN requestor;
GO
ALTER TABLE labpurchasing.purchases ADD requestor int;