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
    }
    else {
        console.error('MCC animalRequests row lacks objectid. row ID: ' + row.rowid);
    }
}

function calculatePreliminaryScore(row) {
    // NOTE: the initial score is two, such that the final range is 0-10
    var score = 2;

    score += row.earlystageinvestigator ? 1 : 0;
    if (row.institutiontype === 'Minority serving' || row.institutiontype === 'University/Non-profit') {
        score += 1;
    }
    else if (row.institutiontype === 'Commercial entity') {
        score -= 1;
    }
    else {
        console.error('Unknown MCC institutiontype: ' + row.institutiontype)
    }

    if (['NIH-supported research', 'Other federal agency support', 'Institutional start-up funding', 'Foundation/non-profit support'].indexOf(row.fundingsource) !== -1) {
        score += 1;
    }
    else if (row.fundingsource === 'Private funding' || row.fundingsource === 'Not currently funded') {
        // no score change
    }
    else {
        console.error('Unknown MCC fundingsource: ' + row.institutiontype)
    }

    score += row.existingnhpfacilities === 'Existing NHP facilities' ? 1 : -1;
    score += row.existingmarmosetcolony === 'Existing marmoset colony' ? 1 : 0;

    //TODO: verify values
    score += row.isbreedinganimals ? 1 : 0;

    return score;
}