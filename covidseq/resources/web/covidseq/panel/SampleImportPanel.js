Ext4.define('CovidSeq.panel.SampleImportPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.covidseq-sampleimportpanel',

    COLUMNS: [{
        name: 'workbook',
        labels: ['Experiment/Workbook', 'Expt', 'Expt #', 'Experiment', 'Exp#', 'Exp #', 'Workbook', 'Workbook #'],
        alwaysShow: true,
        transform: 'expt',
        allowBlank: true
    },{
        name: 'samplename',
        labels: ['CV', 'CODED_ID', 'CV#', 'CV #'],
        alwaysShow: true,
        allowBlank: false,
        transform: 'samplename'
    },{
        name: 'cdna_plate_location',
        labels: ['Well', 'Well Id', 'cDNA_Plate_Location'],
        alwaysShow: true,
        allowBlank: true
    },{
        name: 'cdna_plate_id',
        labels: ['cDNA_Plate_ID', 'RQG_alt_ID', 'MM Alt ID', 'Alt ID', 'MM ID', 'RQG_alt_ID'],
        alwaysShow: true,
        allowBlank: true
    },{
        name: 'gender',
        labels: ['Gender', 'Sex'],
        allowBlank: true,
        alwaysShow: true,
        transform: 'gender'
    },{
        name: 'state',
        labels: ['State'],
        allowBlank: true,
        transform: 'state'
    },{
        name: 'county',
        labels: ['County'],
        allowBlank: true,
        transform: 'county'
    },{
        name: 'country',
        labels: ['Country'],
        allowBlank: true
    },{
        name: 's_probe',
        labels: ['S', 'N1_or_S'],
        allowBlank: true
    },{
        name: 'n_probe',
        labels: ['N/N2', 'N2_orN'],
        allowBlank: true
    },{
        name: 'ms_probe',
        labels: ['MS2/Control', 'MS2', 'RP_or_ORF1ab'],
        allowBlank: true
    },{
        name: 'age',
        labels: ['Age'],
        allowBlank: true
    },{
        name: 'sampledate',
        labels: ['Sample Date', 'Collection Date', 'CollectionDate', 'SampleDate', 'Collection_Date'],
        alwaysShow: true,
        allowBlank: true
    },{
        name: 'assayType',
        labels: ['Assay Type', 'Assay_Type'],
        allowBlank: true
    },{
        name: 'comment',
        labels: ['Comment'],
        alwaysShow: true,
        allowBlank: true
    },{
        name: 'objectid',
        labels: ['Key'],
        allowBlank: true
    }],

    IGNORED_COLUMNS: [],

    padDigits: function(n, totalDigits){
        n = n.toString();
        var pd = '';
        if (totalDigits > n.length){
            for (var i=0; i < (totalDigits-n.length); i++){
                pd += '0';
            }
        }
        return pd + n;
    },

    transforms: {
        expt: function(val, panel){
            return val || panel.EXPERIMENT;
        },

        country: function(val, panel) {
            return val || 'USA'
        },

        gender: function(val, panel, row, rowIdx, messages) {
            if (!val) {
                return val;
            }

            if (['m', 'male'].indexOf(val.toLowerCase()) > -1) {
                return 'Male';
            }

            if (['f', 'female'].indexOf(val.toLowerCase()) > -1) {
                return 'Female';
            }

            if (val === 'unknown' || val === 'unk') {
                return 'Unknown'
            }

            return val;
        },

        county: function(val, panel, row, rowIdx, messages) {
            if (!val) {
                return val;
            }

            val = val.toLowerCase();
            val = Ext4.String.trim(val);
            val = val.replaceAll('-', '')
            if (val === 'not applicable') {
                return 'Not Applicable'
            }

            if (val === 'unknown' || val === 'unk') {
                return 'Unknown'
            }

            if (panel.COUNTY_MAP[val]) {
                if (row.state) {
                    if (row.state !== panel.COUNTY_MAP[val][2]) {
                        messages.push('State doesnt match for count: ' + val + ', was: '  + row.state + ', expected: ' + panel.COUNTY_MAP[val][2] + ', at row: ' + rowIdx);
                    }
                } else {
                    row.state = panel.COUNTY_MAP[val][2]
                }
                return panel.COUNTY_MAP[val][1]
            }

            return val;
        },

        state: function(val, panel, data) {
            if (!val) {
                data.comment = data.comment ? data.comment + '. State auto-assigned to OR' : 'State auto-assigned to OR';
                return 'OR';

            }

            val = val.toLowerCase();
            if (['or', 'oregon'].indexOf()) {
                return 'OR'
            }
            else if (['wa', 'washington'].indexOf()) {
                return 'WA'
            }

            return val.toUpperCase();
        },
    },

    DEFAULT_HEADER: ['cDNA_Plate_ID','cDNA_Plate_Location','CODED_ID','RQG_alt_ID','Collection_Date','Gender','State','County','N1_or_S','N2_orN','RP_or_ORF1ab','Assay_Type','MS2','Age'],

    initComponent: function () {
        this.COLUMN_MAP = {};
        Ext4.Array.forEach(this.COLUMNS, function(col){
            this.COLUMN_MAP[col.name.toLowerCase()] = col;
            Ext4.Array.forEach(col.labels, function(alias){
                this.COLUMN_MAP[alias.toLowerCase()] = col;
            }, this);
        }, this);

        this.COUNTY_MAP = {};
        Ext4.Array.forEach(this.COUNTY_LIST, function(col){
            this.COUNTY_MAP[Ext4.String.trim(col[0].toLowerCase())] = col;
            this.COUNTY_MAP[Ext4.String.trim(col[1].toLowerCase())] = col;
        }, this);

        Ext4.apply(this, {
            title: null,
            border: false,
            defaults: {
                border: false,
                labelWidth: 250
            },
            items: [{
                style: 'padding-top: 10px;',
                html: 'This page is designed to help import COVID samples, primarily from the MM lab. It can auto-assign the CV# (based on the last known ID), and will auto-create patient records as-needed. There is a preview step after submission, so please review data carefully before submitting.<p>'
            }, {
                layout: 'hbox',
                items: [{
                    xtype: 'button',
                    text: 'Download Template',
                    style: 'margin-bottom: 10px;',
                    border: true,
                    scope: this,
                    handler: function(btn) {
                        LABKEY.Utils.convertToExcel({
                            fileName : 'COVID_Sample_Upload_' + Ext4.Date.format(new Date(), 'Y-m-d H_i_s') + '.xls',
                            sheets : [{
                                name: 'data',
                                data: [this.DEFAULT_HEADER]
                            }]
                        });
                    }
                }]
            }, {
                xtype: 'checkbox',
                fieldLabel: 'Auto-create Patient Records',
                helpPopup: 'If checked, any CV# lacking a patient record will be automatically created',
                itemId: 'autoCreatePatients',
                checked: true
            }, {
                xtype: 'checkbox',
                fieldLabel: 'Auto-assign CV#',
                itemId: 'autoAssignCv',
                checked: true
            }, {
                xtype: 'textfield',
                fieldLabel: 'ID Prefix',
                itemId: 'idPrefix',
                value: 'CV'
            }, {
                xtype: 'triggerfield',
                fieldLabel: 'Starting Number For ID Assignment',
                helpPopup: 'If blank, this will be inferred automatically, based on existing records',
                itemId: 'idNumberStart',
                triggerCls: 'x4-form-search-trigger',
                value: null,
                onTriggerClick: function(){
                    var prefix = this.up('panel').down('#idPrefix').getValue();
                    if (!prefix) {
                        Ext4.Msg.alert('Error', 'Must enter a value for ID prefix');
                        return;
                    }

                    Ext4.Msg.wait('Loading...');
                    LABKEY.Query.selectRows({
                        schemaName: 'covidseq',
                        queryName: 'samples',
                        method: 'POST',
                        filterArray: [LABKEY.Filter.create('samplename', prefix, LABKEY.Filter.Types.STARTS_WITH)],
                        scope: this,
                        columns: 'samplename',
                        failure: LDK.Utils.getErrorCallback(),
                        success: function(results) {
                            let highestVal = 1;
                            Ext4.Array.forEach(results.rows, function (row) {
                                var sampleId = row.samplename;
                                sampleId = sampleId.replaceAll('^' + prefix, '');
                                if (Ext4.isNumeric(sampleId)) {
                                    sampleId = Number(sampleId);
                                    if (sampleId > highestVal) {
                                        highestVal = sampleId
                                    }
                                }
                                else {
                                    console.log('non-numeric: ' + row.samplename + ' / ' + sampleId)
                                }
                            }, this);

                            this.setValue(highestVal);
                            Ext4.Msg.hide();
                        }
                    });
                }
            },{
                xtype: 'textfield',
                style: 'margin-top: 20px;',
                fieldLabel: 'Expt/Workbook',
                itemId: 'exptNum',
                value: LABKEY.Security.currentContainer.type === 'workbook' ? LABKEY.Security.currentContainer.name : null
            }, {
                xtype: 'checkbox',
                fieldLabel: 'Data Lacks Header Row',
                helpPopup: 'If checked, the default header will be injected to the data',
                itemId: 'lacksHeader',
                checked: false
            }, {
                xtype: 'textarea',
                fieldLabel: 'Paste Data Below',
                labelAlign: 'top',
                itemId: 'data',
                width: 1000,
                height: 300
            }, {
                xtype: 'button',
                text: 'Preview',
                border: true,
                scope: this,
                handler: this.onPreview
            }, {
                itemId: 'previewArea',
                style: 'margin-top: 20px;margin-bottom: 10px;',
                autoEl: 'table',
                cls: 'stripe hover'
            }]
        });

        this.callParent(arguments);
    },

    onPreview: function(btn) {
        this.EXPERIMENT = this.down('#exptNum').getValue();

        var text = this.down('#data').getValue();
        if (!text) {
            Ext4.Msg.alert('Error', 'Must provide the table of data');
            return;
        }

        //this is a special case.  if the first character is Tab, this indicates a blank field.  Add a placeholder so it's not trimmed:
        if (text .startsWith("\t")) {
            text = 'Column1' + text;
        }
        text = Ext4.String.trim(text);

        var rows = LDK.Utils.CSVToArray(text, '\t');
        var lacksHeader = this.down('#lacksHeader').getValue();
        var colArray = lacksHeader ? this.parseHeader(Ext4.Array.remove(this.DEFAULT_HEADER, 'CODED_ID')) : this.parseHeader(rows.shift());
        if (!colArray) {
            return;
        }

        var parsedRows = this.parseRows(colArray, rows);
        if (!parsedRows) {
            return;
        }

        console.log(parsedRows)

        this.renderPreview(colArray, parsedRows);
    },

    parseHeader: function(headerRow){
        var colArray = [];
        var colNames = {};
        var error = false;
        Ext4.Array.forEach(headerRow, function(headerText, idx){
            if (!headerText) {
                error = true;
                Ext4.Msg.alert('Error', 'Cannot have blank elements in the header. Index was: ' + (idx + 1));
                return false;
            }

            var colData = this.COLUMN_MAP[headerText.toLowerCase()];
            if (!colData) {
                headerText = Ext4.String.trim(headerText);

                colData = this.COLUMN_MAP[headerText.toLowerCase()];
            }

            if (colData) {
                colNames[colData.name] = idx;
            } else {
                console.warn('Unknown column: ' + headerText);
            }
        }, this);

        if (error) {
            return;
        }

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

    parseRows: function(colArray, rows){
        var ret = [];

        const prefix = this.down('#idPrefix').getValue();
        const startingId = this.down('#idNumberStart').getValue();
        let ntcIdx = 0;
        let idIndex = 0;

        var error = false;
        var errorMessages = [];
        Ext4.Array.forEach(rows, function(row, rowIdx){
            var data = {
                objectId: LABKEY.Utils.generateUUID()
            };

            Ext4.Array.forEach(colArray, function(col, colIdx){
                var cell = Ext4.isDefined(col.dataIdx) ? row[col.dataIdx] : '';
                if (cell){
                    if (cell.toLowerCase) {
                        if (cell.toLowerCase() === 'unk' || cell.toLowerCase() === 'unknown') {
                            cell = 'Unknown'
                        }
                    }

                    if (col.transform && this.transforms[col.transform]){
                        cell = this.transforms[col.transform](cell, this, data, rowIdx, errorMessages);
                    }

                    data[col.name] = cell;
                }
                else {
                    //allow transform even if value is null
                    if (col.transform && this.transforms[col.transform]){
                        cell = this.transforms[col.transform](cell, this, data);
                    }

                    data[col.name] = cell;
                }
            }, this);

            // Optionally auto-assign ID:
            if (!data.samplename && this.down('#autoAssignCv').getValue()) {
                if (!prefix || !startingId) {
                    Ext4.Msg.alert('Error', 'Must provide both the sample prefix (i.e. CV) and a starting ID for sample IDs to be auto-assigned');
                    error = true;
                    return false;
                }

                // Assume this is NTC?
                if (!data.cdna_plate_id && !data.sampledate) {
                    ntcIdx++;
                    data.samplename = 'NTC' + ntcIdx;
                }
                else {
                    data.samplename = prefix + this.padDigits(Number(startingId) + idIndex, 4);
                    idIndex++;
                }
            }

            //TODO:
            // ID test type so probe to CT relationship is known. X=TP=Taqpath (Four probe, second is N gene); CDC=CDC assay (3 probe, first is N gene).
            // Empty well / blank line -> NTC. Consider naming Batch-NTC-X

            ret.push(data);
        }, this);

        if (errorMessages.length) {
            Ext4.Msg.alert('Error', 'There were errors:<br>' + errorMessages.join('<br>'));
            return null;
        }

        return error ? null : ret;
    },

    renderPreview: function(colArray, parsedRows){
        var previewArea = this.down('#previewArea');
        previewArea.removeAll();

        var columns = [{title: 'Row #'}];
        var colIdxs = [];
        Ext4.Array.forEach(colArray, function(col, idx){
            if (col){
                columns.push({title: col.labels[0], className: 'dt-center'});
                colIdxs.push(idx);
            }
        }, this);

        var data = [];
        var missingValues = false;
        Ext4.Array.forEach(parsedRows, function(row, rowIdx){
            var toAdd = [rowIdx + 1];
            Ext4.Array.forEach(colIdxs, function(colIdx){
                var colDef = colArray[colIdx];
                var propName = colDef.name;

                var allowBlank = colDef.allowBlank;
                if (allowBlank === false && Ext4.isEmpty(row[propName])){
                    missingValues = true;
                    toAdd.push('MISSING');
                }
                else {
                    toAdd.push(row[propName] || 'ND');
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

        if (missingValues){
            Ext4.Msg.alert('Error', 'One or more rows is missing data.  Any required cells without values are marked MISSING');
        }
    },

    onSubmit: function(e, dt, node, config){
        //Ext4.Msg.wait('Saving...');

        //TODO
    },

    COUNTY_LIST: [
        ['ADA','Ada County','ID'],
        ['ALAMANCE','Alamance County','NC'],
        ['ALAMEDA','Alameda County','CA'],
        ['ALPENA','Alpena County','MI'],
        ['ANCHORAGE','Anchorage','AK'],
        ['ARLINGTON','Arlington County','VA'],
        ['BEADLE','Beadle County','SD'],
        ['BEAVERTON','Washington County','OR'],
        ['BENTON','Benton County','OR'],
        ['BEXAR','Bexar County','TX'],
        ['BONNEVILLE','Bonneville County','ID'],
        ['BUCKS','Bucks County','PA'],
        ['CHITTENDEN','Chittenden County','VT'],
        ['Clackamas','Clackamas County','OR'],
        ['CLACKAMAS','Clackamas County','OR'],
        ['Clark','Clark County','WA'],
        ['CLARK','Clark County','WA'],
        ['CLATSOP','Clatsop County','OR'],
        ['Coffee','Coffee County','GA'],
        ['Columbia','Columbia County','OR'],
        ['COLUMBIA','Columbia County','OR'],
        ['COOS','Coos County','OR'],
        ['COWLITZ','Cowlitz County','WA'],
        ['Crook','Crook County','OR'],
        ['Curry','Curry County','OR'],
        ['DENVER','Denver County','CO'],
        ['DESCHUTES','Deschutes County','OR'],
        ['DOUGLAS','Douglas County','OR'],
        ['El Dorado','El Dorado County','CA'],
        ['Fairfax','Fairfax County','VA'],
        ['FRANKLIN','Franklin County','WA'],
        ['Frederick','Frederick County','MD'],
        ['FRESNO','Fresno County','CA'],
        ['GRANT','Grant County','OR'],
        ['HAWAII','Hawaii County','HI'],
        ['HILLSBOROUGH','Hillsborough County','FL'],
        ['HONOLULU','Honolulu County','HI'],
        ['HOOD RIVER','Hood River County','OR'],
        ['HUDSON','Hudson County','NJ'],
        ['JACKSON','Jackson County','OR'],
        ['JEFFERSON','Jefferson County','CO'],
        ['JOHNSON','Johnson County','IN'],
        ['JOSEPHINE','Josephine County','OR'],
        ['KING','King County','WA'],
        ['KITSAP','Kitsap County','WA'],
        ['KITTITAS','Kittitas County','WA'],
        ['KLAMATH','Klamath County','OR'],
        ['Klickitat','Klickitat County','WA'],
        ['KLICKITAT','Klickitat County','WA'],
        ['KNOX','Knox County','TN'],
        ['Kootenai','Kootenai County','ID'],
        ['LAFAYETTE','Lafayette Parish','LA'],
        ['Lake','Lake County','OR'],
        ['LANE','Lane County','OR'],
        ['LANE','Lane County','OR'],
        ['LEWIS AND CLARK','Lewis and Clark County','MT'],
        ['LINCOLN','Lincoln County','OR'],
        ['LINN','Linn County','OR'],
        ['LOS ANGELES','Los Angeles County','CA'],
        ['Loudoun','Loudoun County','VA'],
        ['MADERA','Madera County','CA'],
        ['MARICOPA','Maricopa County','AZ'],
        ['MARION','Marion County','OR'],
        ['Mason','Mason County','WA'],
        ['MIAMI-DADE','Miami-Dade County','FL'],
        ['MINIDOKA','Minidoka County','ID'],
        ['MONROE','Monroe County','PA'],
        ['MORROW','Morrow County','OR'],
        ['Multnomah','Multnomah County','OR'],
        ['OAKLAND','Oakland County','CA'],
        ['OKALOOSA','Okaloosa County','FL'],
        ['ORANGE','Orange County','CA'],
        ['Orleans','Orleans Parish','LA'],
        ['PACIFIC','Pacific County','WA'],
        ['Pennington','Pennington County','SD'],
        ['PIERCE','Pierce County','WA'],
        ['PIMA','Pima County','AZ'],
        ['POLK','Polk County','OR'],
        ['PRINCE GEORGES','Prince George\'s County','MD'],
        ['Pulaski','Pulaski County','AK'],
        ['RIVERSIDE','Riverside County','CA'],
        ['SACRAMENTO','Sacramento County','CA'],
        ['SAINT LANDRY','Saint Landry Parish','LA'],
        ['SALT LAKE','Salt Lake County','UT'],
        ['SAN BERNARDINO','San Bernardino County','CA'],
        ['SAN DIEGO','San Diego County','CA'],
        ['SAN JOAQUIN','San Joaquin County','CA'],
        ['SANTA BARBARA','Santa Barbara County','CA'],
        ['SANTA CLARA','Santa Clara County','CA'],
        ['Shasta','Shasta County','CA'],
        ['SHERMAN','Sherman County','OR'],
        ['Siskiyou','Siskiyou County','CA'],
        ['SKAMANIA','Skamania County','WA'],
        ['SNOHOMISH','Snohomish County','WA'],
        ['SPOKANE','Spokane County','WA'],
        ['TARRANT','Tarrant County','TX'],
        ['TETON','Teton County','WY'],
        ['THURSTON','Thurston County','WA'],
        ['Tillamook','Tillamook County','OR'],
        ['TULARE','Tulare County','CA'],
        ['TWIN FALLS','Twin Falls County','ID'],
        ['UMATILLA','Umatilla County','OR'],
        ['UNION','Union County','OR'],
        ['VIRGINIA BEACH CITY','Virginia Beach','VA'],
        ['WAHKIAKUM','Wahkiakum County','WA'],
        ['Wasco','Wasco County','OR'],
        ['WASCO','Wasco County','OR'],
        ['Washington','Washington County','OR'],
        ['WHEELER','Wheeler County','OR'],
        ['WHITMAN','Whitman County','WA'],
        ['YAKIMA','Yakima County','WA'],
        ['YAMHILL','Yamhill County','OR'],
        ['YELLOWSTONE','Yellowstone County','MT']
    ]
});