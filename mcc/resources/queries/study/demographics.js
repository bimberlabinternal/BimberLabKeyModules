/*
 * Copyright (c) 2010-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

require("ehr/triggers").initScript(this);

var triggerHelper = new org.labkey.mcc.query.TriggerHelper(LABKEY.Security.currentUser.id, LABKEY.Security.currentContainer.id);

var skipMccAliasCreation = [];

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

    if (row.skipMccAliasCreation) {
        skipMccAliasCreation.push(row.Id);
    }
}

function onComplete(event, errors, helper){
    if (!helper.isETL() && helper.getPublicParticipantsModified().length) {
        var toAdd = helper.getPublicParticipantsModified();
        if (skipMccAliasCreation.length) {
            for (var i=0;i<skipMccAliasCreation.length;i++){
                var arrIdx = toAdd.indexOf(skipMccAliasCreation[i]);
                if (arrIdx !== -1) {
                    toAdd.splice(arrIdx, 1);
                }
            }
        }

        if (toAdd.length) {
            var aliasesCreated = triggerHelper.ensureMccAliasExists(helper.getPublicParticipantsModified());
            if (aliasesCreated) {
                console.log('Total MCC aliases assigned during import: ' + aliasesCreated);
            }
        }
    }
}
