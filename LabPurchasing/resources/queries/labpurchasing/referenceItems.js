var LABKEY = require("labkey");
var console = require("console");

function beforeInsert(row, errors){
    // The purpose of this is to allow the user to provide a string value for
    // vendorId or vendorName, and attempt to resolve this against known vendors:
    if (!row.vendorId || isNaN(row.vendorId)) {
        var vendorName = row.vendorName || row.vendorId;
        if (vendorName) {
            LABKEY.Query.selectRows({
                schemaName: 'labpurchasing',
                queryName: 'vendors',
                columns: 'rowid',
                filterArray: [LABKEY.Filter.create('vendorName', vendorName)],
                sort: '-rowid',
                maxRows: 1,
                scope: this,
                success: function(results) {
                    if (results.rows.length) {
                        row.vendorId = results.rows[0].rowId;
                    }
                    else {
                        console.error("Unable to resolve vendor: " + vendorName);
                    }
                }
            })
        }
    }
}