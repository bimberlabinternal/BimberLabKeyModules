SELECT
    r1.rowId,
    r1.vendorId,
    r1.itemNumber,

    r1.itemName,
    r1.units,
    r1.unitCost,

    GROUP_CONCAT(DISTINCT r2.rowId, ',') as alternateRowId,
    GROUP_CONCAT(CASE WHEN r1.itemName = r2.itemName THEN null ELSE r2.itemName END, ',') as alternateName,
    GROUP_CONCAT(CASE WHEN r1.units = r2.units THEN null ELSE r2.units END, ',') as alternateUnits,
    GROUP_CONCAT(CASE WHEN r1.unitCost = r2.unitCost THEN null ELSE r2.unitCost END, ',') as alternateUnitCost

FROM labpurchasing.referenceItems r1
JOIN labpurchasing.referenceItems r2 ON (
    r1.rowId != r2.rowId AND
    r1.itemNumber = r2.itemNumber and
    r1.vendorId = r2.vendorId
)

WHERE r1.itemNumber NOT IN ('NA', '1')

GROUP BY r1.rowId, r1.vendorId, r1.itemNumber, r1.itemName, r1.units, r1.unitCost