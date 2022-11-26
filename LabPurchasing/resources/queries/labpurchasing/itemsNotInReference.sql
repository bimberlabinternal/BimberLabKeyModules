SELECT
p.rowId,
p.vendorId,
p.itemName,
p.itemNumber,
p.units,
p.unitCost,
p.orderDate

FROM labpurchasing.purchases p
LEFT JOIN labpurchasing.referenceItems r ON (p.itemNumber = r.itemNumber and p.vendorId = r.vendorId)
WHERE r.rowId IS NULL AND (p.excludeFromRefItems IS NULL OR p.excludeFromRefItems = false)