ALTER TABLE labpurchasing.purchases ADD emailOnArrival boolean DEFAULT true;
ALTER TABLE labpurchasing.purchases ADD excludeFromRefItems boolean DEFAULT false;

ALTER TABLE labpurchasing.purchases DROP COLUMN requestor;
ALTER TABLE labpurchasing.purchases ADD requestor int;