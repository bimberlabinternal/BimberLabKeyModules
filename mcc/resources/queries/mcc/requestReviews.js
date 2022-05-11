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
    if (row.requestId) {
        row.requestId = row.requestId.toUpperCase();
    }
}

function afterInsert(row, errors){
    afterUpsert(row, null, errors);
}

function afterUpdate(row, oldRow, errors){
    afterUpsert(row, oldRow, errors);
}

function afterUpsert(row, oldRow, errors) {
    if (row.review) {
        var requestId = row.requestId || oldRow.requestId
        if (!requestId) {
            console.error('No requestId for requestScore update')
            console.error(row)
            return
        }

        // Only perform this test the first time the review is added
        if (!oldRow || !oldRow.review) {
            triggerHelper.possiblySetRabComplete(requestId);
        }
    }
}