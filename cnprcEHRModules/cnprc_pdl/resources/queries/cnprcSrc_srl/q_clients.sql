SELECT
SC_PK AS key,
SC_CLIENT AS client,
SC_CLIENT_FULLNAME AS clientFullName,
(CASE WHEN SC_ACTIVE_YN = 'Y' THEN 1 ELSE 0 END) AS isActive,
SC_SORT_ORDER AS sortOrder,
SC_COMMENT AS comments,
(CASE WHEN SC_SHOW_ANIDS = 'Y' THEN 1 ELSE 0 END) AS showIds,
FROM cnprcSrc_srl.SRL_CLIENTS