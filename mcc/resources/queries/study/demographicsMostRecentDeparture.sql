select

    T1.Id,
    max(T1.date) as MostRecentDeparture,
    group_concat(distinct t1.mccRequestId) as mccRequestId

FROM study.departure T1
WHERE T1.qcstate.publicdata = true
GROUP BY T1.Id
