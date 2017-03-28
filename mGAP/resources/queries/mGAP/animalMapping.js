var console = require("console");
var LABKEY = require("labkey");

var triggerHelper = new org.labkey.mgap.query.TriggerHelper(LABKEY.Security.currentUser.id, LABKEY.Security.currentContainer.id);

function beforeInsert(row, errors){
    if (row.subjectname) {
        row.externalAlias = triggerHelper.getNextAlias();
    }
}