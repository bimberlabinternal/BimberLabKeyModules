var console = require("console");
var LABKEY = require("labkey");

var triggerHelper = new org.labkey.mcc.query.TriggerHelper(LABKEY.Security.currentUser.id, LABKEY.Security.currentContainer.id);

function beforeInsert(row, errors){
    beforeUpsert(row, null, errors);
}

function beforeUpdate(row, oldRow, errors){
    beforeUpsert(row, oldRow, errors);
}

function beforeUpsert(row, oldRow, errors) {
    row.status = row.status || 'draft'
}

function afterInsert(row, errors){
    afterUpsert(row, null, errors);
}

function afterUpdate(row, oldRow, errors){
    afterUpsert(row, oldRow, errors);
}

function afterUpsert(row, oldRow, errors) {
    if (row.status === 'submitted') {
        if (!oldRow || oldRow.status !== 'submitted') {
            triggerHelper.sendNotification(row.rowid);
        }
    }
}

// cascade delete co-i, cohorts:
function beforeDelete(row, errors){
    if (row.objectid) {
        triggerHelper.cascadeDelete('mcc', 'coinvestigators', 'requestid', row.objectid);
        triggerHelper.cascadeDelete('mcc', 'requestcohorts', 'requestid', row.objectid);
    }
    else {
        console.error('MCC animalRequests row lacks objectid. row ID: ' + row.rowid);
    }
}