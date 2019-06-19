ALTER TABLE mGAP.pedigreeOverrides DROP parentId;

ALTER TABLE mGAP.pedigreeOverrides ADD correctedValue varchar(1000);
ALTER TABLE mGAP.pedigreeOverrides ADD originalValue varchar(1000);