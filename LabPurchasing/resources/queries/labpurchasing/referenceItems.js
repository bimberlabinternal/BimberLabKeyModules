var LABKEY = require("labkey");
var console = require("console");

function beforeInsert(row, errors){
    // Allow resulting vendorId from name:
    if (row.vendorName && !row.vendorId) {
        LABKEY.Query.selectRows({
            schemaName: 'labpurchasing',
            queryName: 'vendors',
            columns: 'rowid',
            filterArray: [LABKEY.Filter.create('vendorName', row.vendorName)],
            sort: '-rowid',
            maxRows: 1,
            scope: this,
            success: function(results) {
                if (results.rows.length) {
                    row.vendorId = results.rows[0].rowId;
                }
                else {
                    console.error("Unable to resolve vendorname: " + row.vendorName);
                }
            }
        })
    }
}