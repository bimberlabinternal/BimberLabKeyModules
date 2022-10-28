Ext4.define('MCC.panel.MccImportPanel', {
    extend: 'Ext.panel.Panel',

    COLUMNS: [{
        name: 'existingRecord',
        labels: ['Existing Record?'],
        allowRowSpan: false,
        alwaysShow: true,
        allowBlank: true
    },{
        name: 'colony',
        labels: ['Current Colony'],
        allowRowSpan: false,
        alwaysShow: true,
        allowBlank: false,
        transform: 'center'
    },{
        name: 'Id',
        labels: ['Id', 'animal ID', 'AnimalId', 'MarmId', 'Marm Id'],
        allowRowSpan: false,
        alwaysShow: true,
        transform: 'animalId',
        allowBlank: false
    },{
        name: 'alternateIds',
        labels: ['Alternate Ids', 'previous Ids'],
        allowRowSpan: false,
        alwaysShow: true,
        transform: 'alternateIds',
        allowBlank: true
    },{
        name: 'source',
        labels: ['Source Colony', 'Source'],
        allowRowSpan: false,
        allowBlank: true,
        alwaysShow: true
    },{
        name: 'species',
        labels: ['Species'],
        allowRowSpan: false,
        allowBlank: true,
        alwaysShow: true,
        transform: 'species'
    },{
        name: 'birth',
        labels: ['Birth', 'DOB'],
        allowRowSpan: false,
        allowBlank: true,
        transform: 'genericDate'
    },{
        name: 'gender',
        labels: ['Sex'],
        allowRowSpan: false,
        allowBlank: true,
        transform: 'sex'
    },{
        name: 'status',
        labels: ['Status'],
        allowRowSpan: false,
        allowBlank: true,
        alwaysShow: true
    },{
        name: 'dam',
        labels: ['Dam', 'maternal ID'],
        allowRowSpan: false,
        allowBlank: true,
        transform: 'damOrSire'
    },{
        name: 'sire',
        labels: ['Sire', 'paternal ID'],
        allowRowSpan: false,
        allowBlank: true,
        transform: 'damOrSire'
    },{
        name: 'weight',
        labels: ['Weight (g)', 'Weight (grams)'],
        allowRowSpan: false,
        allowBlank: true,
        transform: 'weight'
    },{
        name: 'weightDate',
        labels: ['Date of Weight', 'date of weight'],
        alwaysShow: true,
        allowRowSpan: false,
        transform: 'genericDate',
        allowBlank: true
    },{
        name: 'date',
        labels: ['Observation Date', 'date'],
        allowRowSpan: false,
        alwaysShow: true,
        allowBlank: false,
        transform: 'date'
    },{
        name: 'u24_status',
        labels: ['U24 status'],
        alwaysShow: false,
        allowRowSpan: false,
        allowBlank: false,
        transform: 'u24'
    },{
        name: 'availability',
        labels: ['Available to Transfer', 'availalble to transfer'],
        allowRowSpan: false,
        allowBlank: true,
        transform: 'available'
    },{
        name: 'housingStatus',
        labels: ['Current Housing Status'],
        allowRowSpan: false,
        allowBlank: true,
        transform: 'housingStatus'
    },{
        name: 'infantHistory',
        labels: ['Infant History'],
        allowRowSpan: false,
        alwaysShow: true,
        transform: 'infantHistory'
    },{
        name: 'fertilityStatus',
        labels: ['Fertility Status'],
        allowRowSpan: false,
        alwaysShow: true,
        transform: 'fertilityStatus'
    },{
        name: 'medicalHistory',
        labels: ['Medical History'],
        allowRowSpan: false,
        allowBlank: true,
        transform: 'medicalHistory'
    },{
        name: 'errors',
        labels: ['Warnings/Errors'],
        allowRowSpan: false,
        allowBlank: true,
        alwaysShow: true
    }],

    IGNORED_COLUMNS: [],

    stripLeadingNumbers: function(val) {
        if (val) {
            val = val.replace(/^[0-9]( )+-( )+/, '');
        }

        return val;
    },

    enforceAllowableValues: function(val, allowableValues, row) {
        if (!val) {
            return val;
        }

        for (var idx in allowableValues) {
            if (val.toLowerCase() === allowableValues[idx].toLowerCase()) {
                return allowableValues[idx];
            }
        }

        row.errors.push('Bad value: ' + val);

        return(val);
    },

    transforms: {
        species: function(val, panel, row) {
            return val || 'CJ';
        },

        damOrSire: function(val, panel, row) {
            if (val && val.toUpperCase() === 'NA' || val.toUpperCase() === 'N/A') {
                val = null;
            }

            return val;
        },

        center: function(val, panel) {
            return val || panel.CENTER;
        },

        genericDate: function(val, panel, row) {
            if (val) {
                var parsedDate = LDK.ConvertUtils.parseDate(val);
                if (!parsedDate) {
                    row.errors.push('Invalid date: ' + val);
                }
                else {
                    return Ext4.Date.format(parsedDate, 'Y-m-d');
                }
            }

            return val;
        },

        date: function(val, panel, row) {
            val = val || panel.IMPORT_DATE;

            return this.genericDate(val, panel, row);
        },

        sex: function(val, panel, row) {
            val = panel.stripLeadingNumbers(val);
            val = panel.enforceAllowableValues(val, ['male', 'female'], row);

            return(val);
        },

        available: function(val, panel, row) {
            val = panel.stripLeadingNumbers(val);
            val = panel.enforceAllowableValues(val, ['not available for transfer', 'available for transfer'], row);

            return(val);
        },

        housingStatus: function(val, panel, row) {
            val = panel.stripLeadingNumbers(val);
            val = panel.enforceAllowableValues(val, ['singly housed', 'natal family group', 'active breeding', 'social non breeding'], row);

            return(val);
        },

        infantHistory: function(val, panel, row) {
            val = panel.stripLeadingNumbers(val);
            val = panel.enforceAllowableValues(val, ['no experience', 'sibling experience only', 'non successful offspring', 'successful rearing of offspring'], row);

            return(val);
        },

        medicalHistory: function(val, panel, row) {
            val = panel.stripLeadingNumbers(val);
            val = panel.enforceAllowableValues(val, ['naive animal', 'animal assigned to invasive study'], row);

            return(val);
        },

        fertilityStatus: function(val, panel, row) {
            val = panel.stripLeadingNumbers(val);
            val = panel.enforceAllowableValues(val, ['no mating opportunity', 'mated no offspring produced', 'successful offspring produced', 'hormonal birth control', 'sterilized'], row);

            return(val);
        },

        u24: function(val, panel, row) {
            val = panel.stripLeadingNumbers(val);
            val = panel.enforceAllowableValues(val, ['not assigned to U24 breeding colony', 'assigned to U24 breeding colony'], row);

            if (!val) {
                val = 'not assigned to U24 breeding colony';
            }

            return val.toLowerCase() === 'assigned to u24 breeding colony';
        },

        weight: function(val, panel, row){
            // enforce reasonable values and convert to kg
            if (val && val < 20) {
                row.errors.push('Suspicious weight value');
            }

            return val;
        }
    },

    COLUMN_MAP: null,

    initComponent: function () {
        Ext4.QuickTips.init();

        this.COLUMN_MAP = {};
        Ext4.Array.forEach(this.COLUMNS, function(col){
            this.COLUMN_MAP[col.name.toLowerCase()] = col;
            Ext4.Array.forEach(col.labels, function(alias){
                this.COLUMN_MAP[alias.toLowerCase()] = col;
            }, this);
        }, this);

        Ext4.apply(this, {
            title: null,
            border: false,
            defaults: {
                border: false
            },
            items: this.getPanelItems()
        });

        this.callParent(arguments);
    },

    getPanelItems: function(){
        return [{
            style: 'padding-top: 10px;',
            html: 'This page is designed to help import MCC animal-level data. Use the fields below to download the excel template and paste data to import.<p>'
        },{
            layout: 'hbox',
            style: 'margin-bottom: 20px;',
            items: [{
                xtype: 'button',
                text: 'Download Template',
                border: true,
                scope: this,
                href: LABKEY.ActionURL.getContextPath() + '/mcc/exampleData/MCC_Data_Template.xlsx'
            }]
        },{
            xtype: 'datefield',
            fieldLabel: 'Import Date',
            itemId: 'importDate',
            helpPopup: 'This should ideally match the date the sheet was completed. Observations will be listed according to this date',
            value: new Date()
        },{
            xtype: 'textfield',
            fieldLabel: 'Center/Colony Name',
            itemId: 'centerName',
            helpPopup: 'The name of the center submitting these data.',
            value: null
        },{
            xtype: 'textarea',
            fieldLabel: 'Paste Data Below',
            labelAlign: 'top',
            itemId: 'data',
            width: 1000,
            height: 300
        },{
            xtype: 'button',
            text: 'Preview',
            border: true,
            scope: this,
            handler: this.onPreview
        },{
            itemId: 'previewArea',
            style: 'margin-top: 20px;margin-bottom: 10px;',
            autoEl: 'table',
            cls: 'stripe hover'
        }];
    },

    onPreview: function(btn) {
        var text = this.down('#data').getValue();
        if (!text) {
            Ext4.Msg.alert('Error', 'Must provide the table of data');
            return;
        }

        this.CENTER = this.down('#centerName').getValue();
        if (!this.CENTER) {
            Ext4.Msg.alert('Error', 'Must provide the center name');
            return;
        }

        this.IMPORT_DATE = this.down('#importDate').getValue();
        if (this.IMPORT_DATE) {
            this.IMPORT_DATE = Ext4.Date.format(this.IMPORT_DATE, 'Y-m-d');
        }

        //this is a special case.  if the first character is Tab, this indicates a blank field.  Add a placeholder so it's not trimmed:
        if (text .startsWith("\t")) {
            text = 'Column1' + text;
        }
        text = Ext4.String.trim(text);

        text = text.replaceAll('(MM/DD/YYYY)', '');
        text = text.replaceAll('(MM/DD/YY)', '');

        var rows = LDK.Utils.CSVToArray(text, '\t');
        var colArray = this.parseHeader(rows.shift());
        var errorsMsgs = [];
        var parsedRows = this.parseRows(colArray, rows, errorsMsgs);

        // Load existing demographics:
        Ext4.Msg.wait('Loading...');
        LABKEY.Query.selectRows({
            schemaName: 'study',
            queryName: 'demographics',
            columns: 'Id,alternateIds,dam,sire,birth,death,colony,objectid,lsid,mccAlias/externalId',
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(results) {
                Ext4.Msg.hide();
                var demographicsRecords = this.parseDemographics((results.rows));
                parsedRows = this.mergeWithDemographics(parsedRows, demographicsRecords, errorsMsgs);

                //TODO: merge in parents??

                var encounteredIds = [];
                var duplicateIds = [];
                Ext4.Array.forEach(parsedRows, function(row) {
                    if (row.Id && encounteredIds.indexOf(row.Id) > -1) {
                        duplicateIds.push(row.Id);
                        row.errors.push('Duplicate Id: ' + row.Id);
                    }
                    else {
                        encounteredIds.push(row.Id);
                    }
                }, this);

                if (duplicateIds.length) {
                    duplicateIds = Ext4.unique(duplicateIds);
                    errorsMsgs.push('Duplicate Ids: ' + duplicateIds.join(', '));
                }

                if (errorsMsgs.length) {
                    errorsMsgs = Ext4.unique(errorsMsgs);
                    Ext4.Msg.alert('Error', errorsMsgs.join('<br>'));
                    return null;
                }

                this.renderPreview(colArray, parsedRows);
            }
        });
    },

    mergeWithDemographics: function(rows, demographicsRecords, errorMsgs) {
        Ext4.Array.forEach(rows, function(row){
            row.existingRecord = row.Id && demographicsRecords.allIds.indexOf(row.Id) > -1;
            if (row.existingRecord) {
                var existingRecord = demographicsRecords.rowMap[row.Id];
                if (existingRecord.colony !== row.colony) {
                    row.errors.push('Colony does not match existing row: ' + existingRecord.colony);
                }
                else {
                    row.objectId = existingRecord.objectid;

                    var fields = ['birth', 'dam', 'sire', 'source'];
                    for (var idx in fields) {
                        var fn = fields[idx];

                        // kind of a hack:
                        if (fn === 'birth' && existingRecord[fn]) {
                            existingRecord[fn] = Ext4.Date.format(LDK.ConvertUtils.parseDate(existingRecord[fn]), 'Y-m-d');
                        }
                        if (row[fn] && existingRecord[fn] && row[fn] !== existingRecord[fn]) {
                            row.errors.push('Does not match existing row for ' + fn + ': ' + existingRecord[fn]);
                        }
                    }
                }

            }

            //TODO: look for alternateIds?
            //TODO: what about dam/sire?
        }, this);

        return rows;
    },

    parseDemographics: function(rows) {
        var ret = {
            idsByCenter: {},
            alternateIdsByCenter: {},

            allIds: [],
            allAlternateIds: [],

            rowMap: {}
        }

        Ext4.Array.forEach(rows, function(row){
            var center = row.center || 'None';
            ret.idsByCenter[center] = ret.idsByCenter[center] || [];
            ret.idsByCenter[center].push(row.Id);

            ret.allIds.push(row.Id);
            ret.rowMap[row.Id] = row;

            if (row.alternateIds) {
                var alterateIds = LDK.Utils.textToArray(row.alternateIds);
                ret.alternateIdsByCenter[center] = ret.alternateIdsByCenter[center] || [];
                ret.alternateIdsByCenter[center] = ret.alternateIdsByCenter[center].concat(alterateIds);

                ret.allAlternateIds = ret.allAlternateIds.concat(alterateIds);
            }
        }, this);

        return ret;
    },

    parseHeader: function(headerRow){
        var colArray = [];
        var colNames = {};
        Ext4.Array.forEach(headerRow, function(headerText, idx){
            headerText = headerText.replace(/\n/, '');
            headerText = headerText.replace(/\r/, '');
            var colData = this.COLUMN_MAP[headerText.toLowerCase()];
            if (!colData) {
                //replace common terms:
                if (headerText.match(/ng\/ul/i) || headerText.match(/qubit/i)) {
                    headerText = headerText.replace(/( )+(\()*ng\/ul(\))*/i, '');
                    headerText = headerText.replace(/( )+(\()*qubit(\))*/i, '');
                    headerText = Ext4.String.trim(headerText);
                    if (!headerText.match(/Conc/i)) {
                        headerText = headerText + ' Conc';
                    }
                }
                headerText = headerText.replace(/CiteSeq/i, 'Cite-Seq');
                headerText = headerText.replace(/Cite Seq/i, 'Cite-Seq');
                headerText = headerText.replace(/^MS /i, 'MultiSeq ');
                headerText = headerText.replace(/Multi Seq/i, 'MultiSeq');
                headerText = headerText.replace(/Multi-Seq/i, 'MultiSeq');
                headerText = headerText.replace(/Library Index/i, 'Index');
                headerText = headerText.replace(/ RP#/i, '');
                headerText = headerText.replace(/:( )+10X Plate N Set A/i, '');
                headerText = headerText.replace(/:( )+10X Plate T Kit A/i, '');

                headerText = headerText.replace(/5'[- ]*GEX/i, 'GEX');
                headerText = headerText.replace(/5[- ]GEX/i, 'GEX');
                headerText = Ext4.String.trim(headerText);

                colData = this.COLUMN_MAP[headerText.toLowerCase()];
            }

            if (colData){
                colNames[colData.name] = idx;
            }
        }, this);

        Ext4.Array.forEach(this.COLUMNS, function(colData, idx){
            if (this.IGNORED_COLUMNS.indexOf(colData.name) > -1) {
                return;
            }

            if (colData.alwaysShow || colData.allowBlank === false || colNames[colData.name]){
                colData = Ext4.apply({}, colData);
                colData.dataIdx = colNames[colData.name];

                colArray.push(colData);
            }
        },this);

        return colArray;
    },

    parseRows: function(colArray, rows, errorsMsgs){
        var ret = [];
        Ext4.Array.forEach(rows, function(row, rowIdx){
            var data = {
                objectId: LABKEY.Utils.generateUUID(),
                errors: []
            };

            Ext4.Array.forEach(colArray, function(col, colIdx){
                // Handle errors separately:
                if (col.name === 'errors') {
                    return;
                }

                var cell = Ext4.isDefined(col.dataIdx) ? row[col.dataIdx] : '';
                if (col.transform && this.transforms[col.transform]){
                    cell = this.transforms[col.transform](cell, this, data, errorsMsgs);
                }

                data[col.name] = cell;
            }, this);

            if (data.weight && !data.weightDate) {
                data.errors.push('Weight provided, but missing weight date');
            }

            if (data.birth && !data.death) {
                data.status = 'Alive';
            }

            this.checkSubjectId(data, 'Id')
            this.checkSubjectId(data, 'dam')
            this.checkSubjectId(data, 'sire')

            ret.push(data);
        }, this);

        return ret;
    },

    checkSubjectId: function(data, fn) {
        if (!data[fn]) {
            return
        }

        if (data[fn].match(' ')) {
            data.errors.push(fn + ' contains spaces - this should probably not be allowed');
        }

        if (data[fn].match(/[\(\)]/)) {
            data.errors.push(fn + ' contains parentheses - this should probably not be allowed');
        }
    },

    renderPreview: function(colArray, parsedRows){
        var previewArea = this.down('#previewArea');
        previewArea.removeAll();

        var columns = [{title: 'Row #'}];
        var colIdxs = [];
        Ext4.Array.forEach(colArray, function(col, idx){
            if (col){
                var colDef = {title: col.labels[0], className: 'dt-center'};
                if (col.name === 'errors') {
                    colDef.width = '350px';
                    colDef.className = 'dt-left';
                }

                columns.push(colDef);
                colIdxs.push(idx);
            }
        }, this);

        var data = [];
        var missingValues = false;
        var hasErrors = false;
        Ext4.Array.forEach(parsedRows, function(row, rowIdx){
            var toAdd = [rowIdx + 1];
            Ext4.Array.forEach(colIdxs, function(colIdx){
                var colDef = colArray[colIdx];
                var propName = colDef.name;

                if (colDef.allowBlank === false && Ext4.isEmpty(row[propName])){
                    missingValues = true;
                    toAdd.push('MISSING');
                }
                else {
                    var val = row[propName];
                    if (Ext4.isArray(val)) {
                        val = val.join('<br>');
                        toAdd.push(val || '');
                    }
                    else {
                        toAdd.push(Ext4.isEmpty(val) ? '--' : val);
                    }
                }

                if (row.errors && row.errors.length) {
                    hasErrors = true;
                }

            }, this);

            data.push(toAdd);
        }, this);

        var id = '#' + previewArea.getId();
        if ( jQuery.fn.dataTable.isDataTable(id) ) {
            jQuery(id).DataTable().destroy();
        }

        jQuery(id).DataTable({
            data: data,
            pageLength: 500,
            dom: 'rt<"bottom"BS><"clear">',
            buttons: missingValues ? [] : [{
                text: 'Submit',
                action: this.onSubmit,
                rowData: {
                    colArray: colArray,
                    parsedRows: parsedRows,
                    panel: this
                }
            }],
            columns: columns
        });

        previewArea.doLayout();

        if (missingValues || hasErrors){
            Ext4.Msg.alert('Error', 'One or more rows is missing data or has errors.  Any required cells without values are marked MISSING. Warnings/errors are shown to the right.');
        }
    },

    onSubmit: function(e, dt, node, config){
        Ext4.Msg.wait('Saving...');
        var rawData = config.rowData.parsedRows;

        var demographicsInserts = [];
        var demographicsUpdates = [];

        var observationInserts = [];
        var weightInserts = [];

        Ext4.Array.forEach(rawData, function(row){
            if (row.existingRecord) {
                demographicsUpdates.push({
                    Id: row.Id,
                    date: row.date,
                    birth: row.birth,
                    species: row.species,
                    dam: row.dam,
                    sire: row.sire,
                    alternateIds: row.alternateIds,
                    colony: row.colony,
                    source: row.source,
                    gender: row.gender,
                    u24_status: row.u24_status,
                    objectId: row.objectid,
                    calculated_status: row.status,
                    QCStateLabel: 'Completed'
                });
            }
            else {
                demographicsInserts.push({
                    Id: row.Id,
                    date: row.date,
                    birth: row.birth,
                    species: row.species,
                    dam: row.dam,
                    sire: row.sire,
                    alternateIds: row.alternateIds,
                    colony: row.colony,
                    source: row.source,
                    gender: row.gender,
                    u24_status: row.u24_status,
                    objectId: LABKEY.Utils.generateUUID().toUpperCase(),
                    calculated_status: row.status,
                    QCStateLabel: 'Completed'
                });
            }

            if (row.weight && row.weightDate) {
                weightInserts.push({
                    Id: row.Id,
                    date: row.weightDate,
                    // data are stored in kg:
                    weight: row.weight / 1000,
                    objectId: LABKEY.Utils.generateUUID().toUpperCase(),
                    QCStateLabel: 'Completed'
                });
            }

            var obsFieldMap = {
                availability: 'Availability',
                housingStatus: 'Current Housing Status',
                fertilityStatus: 'Fertility Status',
                infantHistory: 'Infant History',
                medicalHistory: 'Medical History'
            }

            Ext4.Array.forEach(Ext4.Object.getKeys(obsFieldMap), function(fn){
                if (row[fn]) {
                    observationInserts.push({
                        Id: row.Id,
                        date: row.date,
                        category: obsFieldMap[fn],
                        observation: row[fn],
                        objectId: LABKEY.Utils.generateUUID().toUpperCase(),
                        QCStateLabel: 'Completed'
                    });
                }
            }, this);
        })

        var commands = [];
        if (demographicsInserts.length) {
            commands.push({
                command: 'insert',
                schemaName: 'study',
                queryName: 'demographics',
                rows: demographicsInserts
            });
        }

        if (demographicsUpdates.length) {
            commands.push({
                command: 'update',
                schemaName: 'study',
                queryName: 'demographics',
                rows: demographicsUpdates
            });
        }

        if (weightInserts.length) {
            commands.push({
                command: 'insert',
                schemaName: 'study',
                queryName: 'weight',
                rows: weightInserts
            });
        }

        if (observationInserts.length) {
            commands.push({
                command: 'insert',
                schemaName: 'study',
                queryName: 'clinical_observations',
                rows: observationInserts
            });
        }

        LABKEY.Query.saveRows({
            commands: commands,
            scope: this,
            success: function(){
                Ext4.Msg.hide();
                Ext4.Msg.alert('Success', 'Data Saved!' +
                        '<br>Demographic Records Created: ' + demographicsInserts.length +
                        '<br>Demographic Records Updated: ' + demographicsUpdates.length +
                        '<br>Weight Records Created: ' + weightInserts.length +
                        '<br>Observation Records Created: ' + observationInserts.length, function(){
                    window.location = LABKEY.ActionURL.buildURL('project', 'begin.view');
                }, this);
            },
            failure: LDK.Utils.getErrorCallback()
        });
    }
});
