var console = require("console");
var LABKEY = require("labkey");
require("ehr/triggers").initScript(this);

var triggerHelper = new org.labkey.mcc.query.TriggerHelper(LABKEY.Security.currentUser.id, LABKEY.Security.currentContainer.id);

EHR.Server.TriggerManager.registerHandlerForQuery(EHR.Server.TriggerManager.Events.ON_BECOME_PUBLIC, 'study', 'Departure', function(scriptErrors, helper, row, oldRow) {
    if (row.destination) {
        triggerHelper.updateDemographicsColony(row.Id, row.destination);
    }
});