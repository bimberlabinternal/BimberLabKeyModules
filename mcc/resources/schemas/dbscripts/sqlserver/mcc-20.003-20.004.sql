CREATE TABLE mcc.animalRequests (
	rowid 									int identity(1,1) 	NOT NULL,
	objectid 								VARCHAR(40)   			NOT NULL,
	lastName 								VARCHAR(1000) 			NOT NULL,
	firstName 							VARCHAR(1000) 			NOT NULL,
	middleInitial 					VARCHAR(10) 				NOT NULL,
	isPrincipalInvestigator BOOLEAN 						NOT NULL,
	institutionName 				VARCHAR(1000) 			NOT NULL,
	institutionCity 				VARCHAR(1000) 			NOT NULL,
	institutionState 				VARCHAR(1000) 			NOT NULL,
	institutionCountry 			VARCHAR(1000) 			NOT NULL,
	institutionType 				VARCHAR(50) 				NOT NULL,
	officialLastName 				VARCHAR(1000) 			NOT NULL,
	officialFirstName 			VARCHAR(1000) 			NOT NULL,
	officialEmail 					VARCHAR(1000) 			NOT NULL,
	coInvestigators 				TEXT,
	fundingSource 					VARCHAR(50) 				NOT NULL,
	experimentalRationale 	VARCHAR(4000) 			NOT NULL,
	numberOfAnimals 				SMALLINT 						NOT NULL,
	otherCharacteristics 		VARCHAR(4000) 			NOT NULL,
	methodsProposed 				VARCHAR(4000) 			NOT NULL,
	collaborations 					VARCHAR(4000) 			NOT NULL,
	isBreedingAnimals 			BOOLEAN 						NOT NULL,
	ofInterestCenters 			VARCHAR(4000) 			NOT NULL,
	researchArea 						VARCHAR(50) 				NOT NULL,
	otherJustification 			VARCHAR(255),
	existingMarmosetColony  BOOLEAN 						NOT NULL,
	existingNHPFacilities   BOOLEAN 						NOT NULL,
	animalWellfare 					VARCHAR(4000) 			NOT NULL,
	vetLastName 						VARCHAR(1000) 			NOT NULL,
	vetFirstName            VARCHAR(1000) 			NOT NULL,
	vetEmail 								VARCHAR(1000) 			NOT NULL,
	iacucApproval 					VARCHAR(50)   			NOT NULL,

	CONSTRAINT PK_animalRequests PRIMARY KEY (rowid)
);


CREATE TABLE mcc.coinvestigators (
	rowid 									int identity(1,1) 	NOT NULL,
	requestid 							VARCHAR(40)   			NOT NULL,
	lastName 								VARCHAR(1000) 			NOT NULL,
	firstName 							VARCHAR(1000) 			NOT NULL,
	middleInitial 					VARCHAR(10) 				NOT NULL,
	institutionName 				VARCHAR(1000) 			NOT NULL,

	CONSTRAINT PK_coinvestigators PRIMARY KEY (rowid)
);
