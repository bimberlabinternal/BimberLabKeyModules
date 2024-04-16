var LABKEY = require("labkey");
var console = require("console");

var triggerHelper = new org.labkey.labpurchasing.LabPurchasingTriggerHelper(LABKEY.Security.currentUser.id, LABKEY.Security.currentContainer.id);

function beforeInsert(row, errors){
    beforeUpsert(row, errors)
}

function beforeUpdate(row, oldRow, errors){
    beforeUpsert(row, errors)
}

function beforeUpsert(row, errors){
    // Validate requestor:
    if (row.requestor) {
        if (!isNaN(row.requestor) && !triggerHelper.isValidUserId(row.requestor)) {
            errors.requestor = 'Unknown userId for requestor: ' + row.requestor;
        }
        // Try to resolve strings:
        else if (isNaN(row.requestor)) {
            var id = triggerHelper.resolveUserId(String(row.requestor));
            if (!id) {
                errors.requestor = 'Unknown userId for requestor: ' + row.requestor;
            }
            else {
                row.requestor = id;
            }
        }
    }

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