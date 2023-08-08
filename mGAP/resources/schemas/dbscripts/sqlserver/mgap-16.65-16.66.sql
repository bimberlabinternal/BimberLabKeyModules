ALTER TABLE mGAP.annotations ADD toolName varchar(1000);
ALTER TABLE mGAP.annotations ADD formatString varchar(1000);
ALTER TABLE mGAP.annotations ADD allowableValues varchar(4000);
ALTER TABLE mGAP.annotations ADD hidden bit;
ALTER TABLE mGAP.annotations ADD isIndexed bit;
