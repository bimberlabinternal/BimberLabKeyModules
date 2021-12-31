SELECT
    t.rowid,
    t.lastname,
    t.firstname,
    t.created,
    t.status,
    t.objectid
FROM mcc.animalRequests t
WHERE ISMEMBEROF(t.createdby)

