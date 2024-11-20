ALTER TABLE mGAP.releaseTracks ADD shouldindex bit default 0;
ALTER TABLE mGAP.releaseTracks ADD vcfIndexId int;

ALTER TABLE mGAP.tracksPerRelease ADD shouldindex bit default 0;
ALTER TABLE mGAP.tracksPerRelease ADD vcfIndexId int;