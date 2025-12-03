SELECT
    s.rowId,
    s.labelOrName,
    s.description,
    group_concat(DISTINCT a.cohortId.labelOrName, char(10)) as cohorts,
    count(DISTINCT a.Id) as numAnimals,
    group_concat(DISTINCT a.DataSets.Demographics.sex) as sexes,
    group_concat(DISTINCT a.sivArt.allInfections, char(10)) as challenges,
    group_concat(DISTINCT a.sivArt.artInitiationDPI, char(10)) as artInitiationsDPI,
    group_concat(DISTINCT a.interventions.allInterventions, char(10)) as interventions,


FROM studies.studies s
LEFT JOIN study.assignment a ON (a.cohortId.studyId = s.rowId)

GROUP BY s.rowId, s.labelOrName, s.description