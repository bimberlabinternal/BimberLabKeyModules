-- This is a workaround to allow the lookup_sets system to provide an additional field beyond value/title:
SELECT
    value,
    CASE
        WHEN category = 'true' THEN true
        WHEN category = 'false' THEN false
        ELSE true
    END as alive

FROM ehr_lookups.birth_condition_raw