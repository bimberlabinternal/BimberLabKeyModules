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
            },{
                text: 'Process Missing IDs',
                action: this.processMissingIds,
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

    processMissingIds: function(e, dt, node, config) {
        Ext4.Msg.wait('Loading...');

        var idToColony = {};
        var colonyToId = {};
        config.rowData.parsedRows.forEach(function(row){
            idToColony[row.Id] = row.colony;

            if (!colonyToId[row.colony]) {
                colonyToId[row.colony] = [];
            }

            colonyToId[row.colony].push(row.Id);
        });

        var missingIds = []
        var multi = new LABKEY.MultiRequest();
        for (var colony in colonyToId) {
            multi.add(LABKEY.Query.selectRows, {
                schemaName: 'study',
                queryName: 'demographics',
                columns: 'Id,colony,objectid,lsid,calculated_status',
                filterArray: [
                    LABKEY.Filter.create('colony', colony, LABKEY.Filter.Types.EQUAL),
                    LABKEY.Filter.create('calculated_status', 'Alive', LABKEY.Filter.Types.EQUAL),
                    LABKEY.Filter.create('Id', colonyToId[colony].join(';'), LABKEY.Filter.Types.NOT_IN)
                ],
                scope: this,
                failure: LDK.Utils.getErrorCallback(),
                success: function (results) {
                    if (results.rows.length) {
                        missingIds = missingIds.concat(results.rows);
                    }
                }
            });
        }

        multi.send(function(){
            Ext4.Msg.hide();
            if (missingIds.length) {
                Ext4.create('Ext.window.Window', {
                    bodyStyle: 'padding: 5px;',
                    width: 600,
                    modal: true,
                    title: 'Reconcile Census with Existing IDs',
                    effectiveDate: config.rowData.panel.IMPORT_DATE,
                    defaults: {
                        labelWidth: 200,
                        width: 575,
                    },
                    items: [{
                        html: 'The following IDs are listed for the indicated colony, but were not in your census. Choose any status updates and hit submit:',
                        border: false,
                        style: 'padding-bottom: 10px;'
                    },{
                        layout: {
                            type: 'table',
                            columns: 4
                        },
                        border: false,
                        defaults: {
                            border: false,
                            bodyStyle: 'padding: 5px'
                        },
                        items: config.rowData.panel.getAnimalRows(missingIds)
                    }],
                    buttons: [{
                        text: 'Update IDs',
                        scope: this,
                        handler: function(btn) {
                            var demographicsUpdates = [];
                            var deathInserts = [];
                            var departureInserts = [];
                            var win = btn.up('window');

                            var missingValues = false;
                            win.query('combo[dataIndex="status_code"]').forEach(function(f){
                                if (f.getValue() && f.getValue() !== f.sourceRecord.calculated_status) {
                                    var fields = win.query('field[recordIdx=' + f.recordIdx + ']');
                                    LDK.Assert.assertEquality('Incorrect number of MccImportPanel fields', 3, fields.length);

                                    var dateVal = fields[1].getValue();
                                    var otherVal = fields[2].getValue();
                                    if (!dateVal || ! otherVal) {
                                        missingValues = true;
                                        return false;
                                    }

                                    if (f.getValue() === 'Dead') {
                                        deathInserts.push({
                                            Id: f.sourceRecord.Id,
                                            objectId: null,
                                            QCStateLabel: 'Completed',
                                            QCState: null,
                                            date: dateVal,
                                            cause: otherVal
                                        });
                                    } else if (f.getValue() === 'Shipped') {
                                        deathInserts.push({
                                            Id: f.sourceRecord.Id,
                                            objectId: null,
                                            QCStateLabel: 'Completed',
                                            QCState: null,
                                            date: dateVal,
                                            destination: otherVal
                                        });
                                    } else {
                                        // Handle unknown:
                                        demographicsUpdates.push({
                                            Id: f.sourceRecord.Id,
                                            calculated_status: f.getValue(),
                                            lsid: f.sourceRecord.lsid,
                                            objectid: f.sourceRecord.objectid
                                        });
                                    }
                                }
                            });

                            if (missingValues) {
                                Ext4.Msg.alert('Error', 'One or more fields is missing a value');
                                return;
                            }

                            var commands = [];
                            if (demographicsUpdates.length) {
                                commands.push({
                                    command: 'update',
                                    schemaName: 'study',
                                    queryName: 'demographics',
                                    rows: demographicsUpdates
                                });
                            }

                            if (departureInserts.length) {
                                commands.push({
                                    type: 'insert',
                                    schemaName: 'study',
                                    queryName: 'departure',
                                    rows: departureInserts
                                });
                            }

                            if (deathInserts.length) {
                                commands.push({
                                    command: 'insert',
                                    schemaName: 'study',
                                    queryName: 'deaths',
                                    rows: deathInserts
                                });
                            }

                            if (!commands.length) {
                                Ext4.Msg.alert('No updates', 'No changes, nothing to do');
                                btn.up('window').close();
                            }
                            else {
                                Ext4.Msg.wait('Saving rows...');
                                LABKEY.Query.saveRows({
                                    commands: commands,
                                    scope: this,
                                    success: function() {
                                        Ext4.Msg.hide();
                                        Ext4.Msg.alert('Success', 'Records updated', function(){
                                            btn.up('window').close();
                                        }, this);
                                    },
                                    failure: LDK.Utils.getErrorCallback()
                                });
                            }
                        }

                    },{
                        text: 'Cancel',
                        handler: function(btn) {
                            btn.up('window').close();
                        }
                    }]
                }).show();
            }
            else {
                Ext4.Msg.alert('No missing IDs', 'All existing IDs from these colonies were present in this census, nothing to do');
            }
        }, this);
    },

    getAnimalRows: function(missingIds) {
        var ret = [{
            xtype: 'displayfield',
            width: 125,
            value: 'Animal Id'
        }, {
            xtype: 'displayfield',
            width: 100,
            value: 'Status'
        }, {
            xtype: 'displayfield',
            width: 100,
            value: 'Date'
        },{
            xtype: 'displayfield',
            width: 125,
            value: 'Destination/Cause'
        }];

        Ext4.Array.forEach(missingIds, function(r, idx){
            ret = ret.concat([{
                xtype: 'displayfield',
                width: 125,
                value: r.Id + ' / ' + r.colony
            }, {
                xtype: 'ldk-simplecombo',
                storeValues: Ext4.Array.unique([r.calculated_status, 'Alive', 'Dead', 'Shipped', 'Unknown']),
                recordIdx: idx,
                dataIndex: 'status_code',
                forceSelection: true,
                sourceRecord: r,
                width: 100,
                style: 'margin-right: 5px',
                value: r.calculated_status,
                listeners: {
                    render: function (f) {
                        if (f.getValue()) {
                            f.fireEvent('change', f, f.getValue());
                        }
                    },
                    change: function (field, val) {
                        var target1 = field.up('panel').down('container[recordIdx=' + field.recordIdx + '][areaType="date"]');
                        target1.removeAll();

                        var target2 = field.up('panel').down('container[recordIdx=' + field.recordIdx + '][areaType="other"]');
                        target2.removeAll();

                        var effectiveDate = field.up('window').effectiveDate;

                        if (val === 'Shipped') {
                            target1.add({
                                xtype: 'datefield',
                                dataIndex: 'date',
                                labelAlign: 'top',
                                recordIdx: field.recordIdx,
                                style: 'margin-right: 5px',
                                value: effectiveDate
                            });

                            target2.add({
                                xtype: 'ldk-simplelabkeycombo',
                                dataIndex: 'destination',
                                labelAlign: 'top',
                                recordIdx: field.recordIdx,
                                schemaName: 'ehr_lookups',
                                queryName: 'source',
                                valueField: 'code',
                                displayField: 'code',
                                forceSelection: true,
                                plugins: ['ldk-usereditablecombo']
                            });
                        }
                        else if (val === 'Dead') {
                            target1.add({
                                xtype: 'datefield',
                                dataIndex: 'date',
                                labelAlign: 'top',
                                recordIdx: field.recordIdx,
                                style: 'margin-right: 5px',
                                value: effectiveDate
                            });

                            target2.add({
                                xtype: 'ldk-simplelabkeycombo',
                                dataIndex: 'cause',
                                labelAlign: 'top',
                                recordIdx: field.recordIdx,
                                schemaName: 'ehr_lookups',
                                queryName: 'death_cause',
                                valueField: 'value',
                                displayField: 'value',
                                forceSelection: true,
                                plugins: ['ldk-usereditablecombo']
                            });
                        }
                    }
                }
            },{
                xtype: 'container',
                recordIdx: idx,
                areaType: 'date'
            },{
                xtype: 'container',
                recordIdx: idx,
                areaType: 'other'
            }]);
        });

        return(ret);
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
