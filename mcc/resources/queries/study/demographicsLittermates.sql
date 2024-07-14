SELECT

d1.Id,
d1.litterId,

GROUP_CONCAT(d2.Id, ',') as litterMates,

FROM study.Demographics d1

JOIN study.Demographics d2 ON (d1.litterId = d2.litterId AND d1.id != d2.id)

WHERE
    d1.qcstate.publicdata = true AND
    d2.qcstate.publicdata = true

GROUP BY d1.Id, d1.litterId