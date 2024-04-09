var LABKEY = require("labkey");
var console = require("console");

var triggerHelper = new org.labkey.labpurchasing.LabPurchasingTriggerHelper(LABKEY.Security.currentUser.id, LABKEY.Security.currentContainer.id);

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
                }
            })
        }
    }
}

function afterInsert(row, errors){
    afterUpsert(row, null, errors);
}

function afterUpdate(row, oldRow, errors){
    afterUpsert(row, oldRow, errors);
}

var toNotify = [];

function afterUpsert(row, oldRow, errors){
    if (row.receivedDate && (!oldRow || !oldRow.receivedDate)) {
        if (row.rowId) {
            toNotify.push(row.rowId);
        }
    }
}

function complete(){
    if (toNotify.length) {
        triggerHelper.sendNotifications(toNotify);
    }
}