ALTER TABLE mcc.requestScores drop column resourceAvailabilityScore;
ALTER TABLE mcc.requestScores add resourceAvailabilityAssessment varchar(4000);
