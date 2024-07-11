/*
 * Copyright (c) 2010-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

require("ehr/triggers").initScript(this);

var triggerHelper = new org.labkey.mcc.query.TriggerHelper(LABKEY.Security.currentUser.id, LABKEY.Security.currentContainer.id);

var additionalIdsModified = [];
var idToMccAlias = {};

function onInit(event, helper){
    helper.setScriptOptions({
        allowAnyId: true,
        requiresStatusRecalc: false,
        allowDatesInDistantPast: true
    });
}

function onUpsert(helper, scriptErrors, row, oldRow){
    if (row.status && row.status.match(/Undetermined/)) {
        row.status = 'Unknown';
    }
    else if (row.status && row.status.match(/Pending Confirmation/)) {
        row.status = 'Unknown';
    }

    if (!row.calculated_status && row.status){
        row.calculated_status = row.status;
    }

    if (row.calculated_status === 'alive') {
        row.calculated_status = 'Alive';
    }
    else if (row.calculated_status === 'Other') {
        row.calculated_status = 'Unknown';
    }

    if (row.death) {
        row.calculated_status = 'Dead';
    }

    if (row.gender) {
        switch (row.gender) {
            case 'M':
            case 'm':
            case 'Male':
            case 'male':
                row.gender = 'm';
                break;
            case 'F':
            case 'f':
            case 'Female':
            case 'female':
                row.gender = 'f';
                break;
            case 'U':
            case 'u':
            case 'Other':
            case 'other':
            case 'Undetermined':
            case 'undetermined':
                row.gender = 'Unknown';
                break;
        }
    }

    if (row.species) {
        switch (row.species) {
            case 'Marmoset':
            case 'marmoset':
            case 'marm':
            case 'CJ':
            case 'cj':
                row.species = 'CJ';
                break;
        }
    }

    if (row.source) {
        switch (row.source) {
            case 'In house':
            case 'Inhouse':
            case 'inhouse':
                row.source = 'In-house';
                break;
        }
    }

    if (row.calculated_status && row.calculated_status.toLowerCase() === 'shipped') {
        row.u24_status = false;
    }

    if (row.mccAlias) {
        idToMccAlias[row.Id] = row.mccAlias;
    }

    if (row.dam) {
        additionalIdsModified.push(row.dam);
        if (row.damMccAlias) {
            idToMccAlias[row.dam] = row.damMccAlias;
        }
    }

    if (row.sire) {
        additionalIdsModified.push(row.sire);
        if (row.sireMccAlias) {
            idToMccAlias[row.sire] = row.sireMccAlias;
        }
    }

    if (oldRow && oldRow.Id) {
        var existingId = triggerHelper.getMccAlias(oldRow.Id);
        if (existingId) {
            idToMccAlias[row.Id] = existingId;
        }
    }

    if (!row.date) {
        row.date = new Date();
    }
}

function onComplete(event, errors, helper){
    if (!helper.isETL()) {
        var toAdd;
        if (helper.getPublicParticipantsModified().length) {
            toAdd = helper.getPublicParticipantsModified();
        }
        else {
            toAdd = [];
        }

        if (additionalIdsModified.length) {
            toAdd = toAdd.concat(additionalIdsModified)
        }

        if (toAdd.length) {
            var aliasesCreated = triggerHelper.ensureMccAliasExists(toAdd, idToMccAlias);
            if (aliasesCreated) {
                console.log('Total MCC aliases assigned during import: ' + aliasesCreated);
            }
        }
    }
}
