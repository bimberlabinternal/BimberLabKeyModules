ALTER TABLE mGAP.releaseTracks ADD shouldindex boolean default false;
ALTER TABLE mGAP.releaseTracks ADD vcfIndexId int;

ALTER TABLE mGAP.tracksPerRelease ADD shouldindex boolean default false;
ALTER TABLE mGAP.tracksPerRelease ADD vcfIndexId int;