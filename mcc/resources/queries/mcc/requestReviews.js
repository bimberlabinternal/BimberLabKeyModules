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
        var requestId = row.requestId || oldRow.requestId;
        if (!requestId) {
            console.error('No requestId for requestReview update')
            console.error(row)
            return
        }

        var reviewerId = row.reviewerId || oldRow.reviewerId;
        if (!reviewerId) {
            console.error('No reviewerId for requestReview')
            console.error(row)
        }
        else {
            // NOTE: send a notification if this is a new row, if this is the first time the row has a reviewerId, or if the reviewerId changed
            if (!oldRow || !oldRow.reviewerId || (oldRow.reviewerId && row.reviewerId && oldRow.reviewerId !== row.reviewerId)) {
                triggerHelper.possiblySendRabNotification(reviewerId);
            }
        }
    }
}