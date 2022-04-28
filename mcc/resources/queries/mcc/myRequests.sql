SELECT
    t.rowid,
    t.lastname,
    t.firstname,
    t.created,
    t.createdby,
    t.status,
    t.objectid,
    t.actions
FROM mcc.animalRequests t
WHERE ISMEMBEROF(t.createdby)

