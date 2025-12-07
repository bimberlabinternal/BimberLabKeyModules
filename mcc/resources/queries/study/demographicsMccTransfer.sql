SELECT
    d.Id,
    GROUP_CONCAT(DISTINCT d.mccRequestId.rowId, ', ') as mccRequestId,
    GROUP_CONCAT(DISTINCT d.mccRequestId.lastName, char(10)) as piLastName,
    GROUP_CONCAT(DISTINCT d.mccRequestId.firstName, char(10)) as piFirstName,
    GROUP_CONCAT(DISTINCT d.mccRequestId.institution, char(10)) as piInstitution
FROM study.departure d
WHERE w.qcstate.publicdata = true AND mccRequestId IS NOT NULL
GROUP BY d.Id