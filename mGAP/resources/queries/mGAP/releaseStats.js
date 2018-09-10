var LABKEY = require("labkey");

function beforeInsert(row, errors){
    row.objectId = row.objectId || LABKEY.Utils.generateUUID();
}