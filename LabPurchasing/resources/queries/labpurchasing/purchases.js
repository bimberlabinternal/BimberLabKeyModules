var LABKEY = require("labkey");
var console = require("console");

var triggerHelper = new org.labkey.labpurchasing.LabPurchasingTriggerHelper(LABKEY.Security.currentUser.id, LABKEY.Security.currentContainer.id);

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