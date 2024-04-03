/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
// ================================================

var console = require("console");
var LABKEY = require("labkey");
var Utils = require("laboratory/Utils").Laboratory.Server.Utils;


console.log("** evaluating: " + this['javax.script.filename']);

function beforeInsert(row, errors){
    validateName(row, errors);
}

function beforeUpdate(row, oldRow, errors){
    validateName(row, errors);
}

function validateName(row, errors){
    if (row.name && row.name.match(/^#/)){
        errors.name = 'Population names cannot begin with #';
    }

    if (row.name && row.name.match(/\/|\\/)){
        errors.name = 'Population names cannot contain slashes';
    }
}