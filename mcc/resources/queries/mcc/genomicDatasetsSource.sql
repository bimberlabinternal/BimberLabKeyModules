SELECT

    r.subjectid as Id,
    r.created as date,
    r.application as datatype,
    r.sraRuns as sra_accession,
    r.totalForwardReads as total_reads

FROM sequenceanalysis.sequence_readsets r

WHERE r.subjectid LIKE 'MCC%' AND r.subjectid NOT LIKE 'MCC[_]%'