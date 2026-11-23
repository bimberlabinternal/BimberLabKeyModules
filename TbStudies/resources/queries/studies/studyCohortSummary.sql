SELECT
    sc.rowId,
    sc.studyId.labelOrName as studyName,
    sc.labelOrName,
    sc.studyId.description,
    count(DISTINCT a.Id) as numAnimals,
    group_concat(DISTINCT a.DataSets.Demographics.sex) as sexes,
    group_concat(DISTINCT a.sivArt.allInfections, char(10)) as challenges,
    group_concat(DISTINCT a.sivArt.artInitiationDPI, char(10)) as artInitiationsDPI,
    group_concat(DISTINCT a.interventions.allInterventions, char(10)) as interventions


FROM studies.studyCohorts sc
LEFT JOIN study.assignment a ON (a.cohortId = sc.rowId)

GROUP BY sc.rowId, sc.labelOrName, sc.studyId.description, sc.studyId.labelOrName