ALTER TABLE tcrdb.clone_responses ADD vGene varchar(1000);
ALTER TABLE tcrdb.clone_responses ADD totalCells int;
ALTER TABLE tcrdb.clone_responses ADD totalCloneSize int;
ALTER TABLE tcrdb.clone_responses ADD fractionCloneActivated double precision;
ALTER TABLE tcrdb.clone_responses ADD totalCellsForSample int;
ALTER TABLE tcrdb.clone_responses ADD oddsRatio double precision;
ALTER TABLE tcrdb.clone_responses ADD enrichmentFDR double precision;
