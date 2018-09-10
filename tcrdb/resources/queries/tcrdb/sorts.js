var console = require("console");
var LABKEY = require("labkey");
var helper = org.labkey.ldk.query.LookupValidationHelper.create(LABKEY.Security.currentContainer.id, LABKEY.Security.currentUser.id, 'tcrdb', 'sorts');
var wellHelper = org.labkey.tcrdb.ImportHelper.create(LABKEY.Security.currentContainer.id, LABKEY.Security.currentUser.id, 'sorts');

var wellMap = wellHelper.getInitialWells();

function beforeInsert(row, errors){
    beforeUpsert(row, null, errors);
}

function beforeUpdate(row, oldRow, errors){
    beforeUpsert(row, oldRow, errors);
}

var rowIdx = -1;

function beforeUpsert(row, oldRow, errors){
    if (row.well){
        row.well = row.well.toUpperCase();
    }

    if (row.population === 'TNF+'){
        row.population = 'TNF-Pos';
    }
    else if (row.population === 'TNF-'){
        row.population = 'TNF-Neg';
    }

    if (row.population && row.population.match(/ï/)){
        row.population = row.population.replace(/ï/g, 'i');
    }

    //check for duplicate plate/well
    oldRow = oldRow || {};
    var rowId = row.rowId || oldRow.rowId || rowIdx;
    rowIdx--;

    var lookupFields = ['stimId'];

    //for 10x-style pooled expts, support 'pool' as a special-case for well name
    var well = row.well || oldRow.well || '';
    if ('pool' !== well.toLowerCase()) {
        var wellArr = [(row.plateId || oldRow.plateId), well];
        var wellKey = wellArr.join('<>').toUpperCase();
        if (wellMap[wellKey] && wellMap[wellKey] !== rowId) {
            errors.well = 'Duplicate entry for plate/well: ' + wellArr.join('/');
        }
        else {
            wellMap[wellKey] = rowId;
        }

        lookupFields.push('well');
    }

    for (var i=0;i<lookupFields.length;i++){
        var f = lookupFields[i];
        var val = row[f];
        if (!LABKEY.ExtAdapter.isEmpty(val)){
            var normalizedVal = helper.getLookupValue(val, f);

            if (LABKEY.ExtAdapter.isEmpty(normalizedVal)){
                errors[f] = ['Unknown value for field: ' + f + '. Value was: ' + val];
            }
            else {
                row[f] = normalizedVal;  //cache value for purpose of normalizing case
            }
        }
    }
}