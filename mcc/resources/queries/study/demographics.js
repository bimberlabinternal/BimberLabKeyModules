/*
 * Copyright (c) 2010-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

require("ehr/triggers").initScript(this);

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
                row.gender = 'm';
                break;
            case 'F':
            case 'f':
            case 'Female':
                row.gender = 'f';
                break;
            case 'U':
            case 'u':
            case 'Other':
            case 'Undetermined':
                row.gender = 'Unknown';
                break;
        }
    }

    if (row.species) {
        switch (row.species) {
            case 'CJ':
            case 'cj':
                row.species = 'CJ';
                break;
        }
    }
}