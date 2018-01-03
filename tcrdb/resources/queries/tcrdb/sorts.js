var console = require("console");
var LABKEY = require("labkey");
var helper = org.labkey.ldk.query.LookupValidationHelper.create(LABKEY.Security.currentContainer.id, LABKEY.Security.currentUser.id, 'tcrdb', 'sorts');

function beforeInsert(row, errors){
    beforeUpsert(row, null, errors);
}

function beforeUpdate(row, oldRow, errors){
    beforeUpsert(row, oldRow, errors);
}

function beforeUpsert(row, oldRow, errors){
    if (row.well){
        row.well = row.well.toUpperCase();
    }

    var lookupFields = ['well', 'stimId'];
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