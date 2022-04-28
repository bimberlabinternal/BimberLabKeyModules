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
    if (!row.status) {
        console.error('Request row being submitted without a status: ' + row.objectid)
    }

    row.status = row.status || 'Draft'

    if (!triggerHelper.hasPermission(row.status)) {
        errors._form = 'Insufficient permissions to update this request';
        return;
    }
}

function afterInsert(row, errors){
    afterUpsert(row, null, errors);
}

function afterUpdate(row, oldRow, errors){
    afterUpsert(row, oldRow, errors);
}

function afterUpsert(row, oldRow, errors) {
    if (row.status && row.status !== 'Draft') {
        triggerHelper.ensureReviewRecordsCreated(row.objectId, row.status, oldRow ? oldRow.status : null, calculatePreliminaryScore(row));
    }
}

// cascade delete co-i, cohorts:
function beforeDelete(row, errors){
    if (!row.status) {
        errors._form = 'Request lacks a status, cannot delete';
        return;
    }

    if (!triggerHelper.hasPermission(row.status)) {
        errors._form = 'Insufficient permissions to delete this request';
        return;
    }

    if (row.objectid) {
        triggerHelper.cascadeDelete('mcc', 'coinvestigators', 'requestid', row.objectid);
        triggerHelper.cascadeDelete('mcc', 'requestcohorts', 'requestid', row.objectid);

        triggerHelper.cascadeDelete('mcc', 'requestreviews', 'requestid', row.objectid);
        triggerHelper.cascadeDelete('mcc', 'requestscores', 'requestid', row.objectid);
    }
    else {
        console.error('MCC animalRequests row lacks objectid. row ID: ' + row.rowid);
    }
}

function calculatePreliminaryScore(row) {
    // NOTE: the initial score is two, such that the final range is 0-10
    var score = 2;

    score += row.earlystageinvestigator ? 1 : 0;
    if (row.institutiontype === 'minorityServing' || row.institutiontype === 'university') {
        score += 1;
    }
    else if (row.institutiontype === 'commercial') {
        score -= 1;
    }
    else {
        console.error('Unknown MCC institutiontype: ' + row.institutiontype)
    }

    if (['nih', 'other-federal', 'start-up', 'foundation'].indexOf(row.fundingsource) !== -1) {
        score += 1;
    }
    else if (row.fundingsource === 'private' || row.fundingsource === 'no-funding') {
        // no score change
    }
    else {
        console.error('Unknown MCC fundingsource: ' + row.fundingsource)
    }

    score += row.existingnhpfacilities === 'existing' ? 1 : -1;
    score += row.existingmarmosetcolony === 'existing' ? 1 : 0;

    //TODO: verify values
    score += row.isbreedinganimals ? 1 : 0;

    return score;
}