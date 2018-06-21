Ext4.define('TCRdb.panel.StimPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.tcrdb-stimpanel',

    initComponent: function(){
        Ext4.apply(this, {
            title: null,
            border: false,
            defaults: {
                border: false
            },
            items: [{
                layout: {
                    type: 'hbox'
                },
                items: [{
                    xtype: 'ldk-integerfield',
                    style: 'margin-right: 5px;',
                    fieldLabel: 'Current Folder/Workbook',
                    labelWidth: 200,
                    minValue: 1,
                    value: LABKEY.Security.currentContainer.type === 'workbook' ? LABKEY.Security.currentContainer.name : null,
                    emptyText: LABKEY.Security.currentContainer.type === 'workbook' ? null : 'Showing All',
                    listeners: {
                        afterRender: function(field){
                            new Ext4.util.KeyNav(field.getEl(), {
                                enter : function(e){
                                    var btn = field.up('panel').down('#goButton');
                                    btn.handler(btn);
                                },
                                scope : this
                            });
                        }
                    }
                },{
                    xtype: 'button',
                    itemId: 'goButton',
                    scope: this,
                    text: 'Go',
                    handler: function(btn){
                        var wb = btn.up('panel').down('ldk-integerfield').getValue();
                        if (!wb){
                            wb = '';
                        }

                        var container = LABKEY.Security.currentContainer.type === 'workbook' ? LABKEY.Security.currentContainer.parentPath + '/' + wb : LABKEY.Security.currentContainer.path + '/' + wb;
                        window.location = LABKEY.ActionURL.buildURL('tcrdb', 'stimDashboard', container);
                    }
                },{
                    xtype: 'button',
                    scope: this,
                    hidden: !LABKEY.Security.currentUser.canInsert,
                    text: 'Create Workbook',
                    handler: function(btn){
                        Ext4.create('Laboratory.window.WorkbookCreationWindow', {
                            abortIfContainerIsWorkbook: false,
                            canAddToExistingExperiment: false,
                            controller: 'tcrdb',
                            action: 'stimDashboard',
                            title: 'Create Workbook'
                        }).show();
                    }
                }]
            },{
                style: 'padding-top: 10px;',
                html: 'This page is designed to help manage samples for the TCR sequencing project.  Where possible we try to carry sample information from to step to step; however, each step often generates new info we need to track, and sometimes samples and plates generated at different times are combined for later steps.  The basic steps are:<p>'
            }]
        });

        this.callParent(arguments);

        Ext4.Msg.wait('Loading...');
        this.loadData();
    },

    getFolderSummaryConfig: function(){

    },

    loadData: function(){
        var multi = new LABKEY.MultiRequest();
        multi.add(LABKEY.Query.selectRows, {
            schemaName: 'laboratory',
            queryName: 'well_layout',
            columns: 'well_96,addressbycolumn_96',
            filterArray: [LABKEY.Filter.create('plate', 1)],
            sort: 'addressbycolumn_96',
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(results){
                this.wellNames96 = [];

                Ext4.Array.forEach(results.rows, function(r){
                    this.wellNames96.push(r.well_96);
                }, this);
            }
        });

        multi.add(LABKEY.Query.selectRows, {
            schemaName: 'sequenceanalysis',
            queryName: 'barcodes',
            sort: 'group_name,tag_name',
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(results){
                this.barcodeMap = {};

                Ext4.Array.forEach(results.rows, function(r){
                    this.barcodeMap[r.group_name] = this.barcodeMap[r.group_name] || {};
                    this.barcodeMap[r.group_name][r.tag_name] = r.sequence;
                }, this);
            }
        });

        multi.add(LABKEY.Query.selectRows, {
            schemaName: 'tcrdb',
            queryName: 'stims',
            columns: 'rowid,tubeNum,animalId,effector,effectors,date,stim,treatment,costim,background,activated,comment,numSorts,status',
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(results){
                this.stimRows = results.rows;
                this.stimStats = {
                    totalStims: 0,
                    lackingSort: 0,
                    hasStatus: 0
                };

                Ext4.Array.forEach(results.rows, function(r){
                    this.stimStats.totalStims++;
                    if (!r.numSorts && !r.status){
                        this.stimStats.lackingSort++;
                    }

                    if (r.status){
                        this.stimStats.hasStatus++;
                    }
                }, this);
            }
        });

        multi.add(LABKEY.Query.selectRows, {
            schemaName: 'tcrdb',
            queryName: 'sorts',
            columns: 'rowid,stimId,stimId/animalId,stimId/effector,stimId/date,stimId/treatment,population,replicate,cells,plateId,well,well/addressByColumn,numLibraries,maxCellsForPlate,container',
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(results){
                this.sortRows = results.rows;
                this.sortStats = {
                    totalSorts: 0,
                    totalPlates: [],
                    lackingLibraries: 0,
                    bulkLackingLibraries: 0,
                    totalPlatesLackingLibraries: [],
                    totalBulkPlatesLackingLibraries: []
                };

                Ext4.Array.forEach(results.rows, function(r){
                    this.sortStats.totalSorts++;
                    if (!r.numLibraries){
                        this.sortStats.lackingLibraries++;

                        if (r.cells > 1) {
                            this.sortStats.bulkLackingLibraries++;
                        }

                        if (r.plateId){
                            this.sortStats.totalPlatesLackingLibraries.push(r.plateId);

                            if (r.maxCellsForPlate > 1){
                                this.sortStats.totalBulkPlatesLackingLibraries.push(r.plateId);
                            }
                        }
                    }

                    if (r.plateId){
                        this.sortStats.totalPlates.push(r.plateId);
                    }
                }, this);

                this.sortStats.totalPlates = Ext4.unique(this.sortStats.totalPlates);
                this.sortStats.totalPlatesLackingLibraries = Ext4.unique(this.sortStats.totalPlatesLackingLibraries);
                this.sortStats.totalBulkPlatesLackingLibraries = Ext4.unique(this.sortStats.totalBulkPlatesLackingLibraries);
            }
        });

        multi.add(LABKEY.Query.selectRows, {
            schemaName: 'tcrdb',
            queryName: 'cdnas',
            columns: 'rowid,sortId,cells,plateId,well,well/addressByColumn,readsetId,readsetId/totalFiles,enrichedReadsetId,enrichedReadsetId/totalFiles,sortId/stimId,sortId/stimId/animalId,sortId/stimId/effector,sortId/stimId/date,sortId/stimId/treatment,sortId/population,sortId/replicate,sortId/cells,sortId/plateId,sortId/sortId/well,sortId/well/addressByColumn,sortId/stimId/stim',
            sort: 'plateId,well/addressByColumn',
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(results){
                this.libraryRows = results.rows;
                this.libraryStats = {
                    totalLibraries: 0,
                    totalPlates: [],
                    lackingAnyReadset: 0,
                    totalPlatesLackingAnyReadset: [],
                    withReadset: 0,
                    totalPlatesWithReadset: [],
                    withTCRReadset: 0,
                    totalPlatesWithTCRReadset: [],
                    lackingBarcodes: 0

                };

                Ext4.Array.forEach(results.rows, function(r){
                    this.libraryStats.totalLibraries++;
                    if (r.readsetId){
                        this.libraryStats.withReadset++;
                        if (r.plateId){
                            this.libraryStats.totalPlatesWithReadset.push(r.plateId);
                        }
                    }

                    if (r.enrichedReadsetId) {
                        this.libraryStats.withTCRReadset++;
                        if (r.plateId){
                            this.libraryStats.totalPlatesWithTCRReadset.push(r.plateId);
                        }
                    }

                    if (!r.enrichedReadsetId && !r.readsetId) {
                        this.libraryStats.lackingAnyReadset++;
                        if (r.plateId){
                            this.libraryStats.totalPlatesLackingAnyReadset.push(r.plateId);
                        }
                    }

                    if (r.plateId){
                        this.libraryStats.totalPlates.push(r.plateId);
                    }
                }, this);

                this.libraryStats.totalPlates = Ext4.unique(this.libraryStats.totalPlates);
                this.libraryStats.totalPlatesWithReadset = Ext4.unique(this.libraryStats.totalPlatesWithReadset);
                this.libraryStats.totalPlatesWithTCRReadset = Ext4.unique(this.libraryStats.totalPlatesWithTCRReadset);
                this.libraryStats.totalPlatesLackingAnyReadset = Ext4.unique(this.libraryStats.totalPlatesLackingAnyReadset);
            }
        });

        multi.add(LABKEY.Query.selectRows, {
            schemaName: 'sequenceanalysis',
            queryName: 'sequence_readsets',
            columns: 'rowid,name,application,totalFiles',
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(results){
                this.readsetRows = results.rows;
                this.readsetStats = {
                    totalReadsets: 0,
                    lackingData: 0,
                    dataImported: 0
                };

                Ext4.Array.forEach(results.rows, function(r){
                    this.readsetStats.totalReadsets++;
                    if (!r.totalFiles){
                        this.readsetStats.lackingData++;
                    }
                    else {
                        this.readsetStats.dataImported++;
                    }
                }, this);
            }
        });

        multi.send(this.onDataLoad, this);
    },

    onDataLoad: function(){
        this.add(this.getItemConfig());

        Ext4.Msg.hide();
    },

    getItemConfig: function(){
        return {
            defaults: {
                border: true,
                style: 'padding-bottom: 10px;',
                bodyStyle: 'padding: 5px;'
            },
            items: [{
                defaults: {
                    border: false
                },
                title: 'Step 1: Stims/Blood Draws',
                layout: {
                    type: 'table',
                    columns: 2,
                    tdAttrs: { style: 'padding-right: 10px;' }
                },
                items: [{
                    html: 'Total Stims:'
                },{
                    html:  '<a href="' + LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'tcrdb', queryName: 'stims'}) + '">' + this.stimStats.totalStims + '</a>'
                },{
                    html: 'Lacking Sorts:'
                },{
                    html: '<a href="' + LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'tcrdb', queryName: 'stims', 'query.numSorts~eq': 0, 'query.status~isblank': null}) + '">' + this.stimStats.lackingSort + '</a>'
                },{
                    html: 'Non-passing Status:'
                },{
                    html: '<a href="' + LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'tcrdb', queryName: 'stims', 'query.status~isnonblank': 0}) + '">' + this.stimStats.hasStatus + '</a>'
                },{
                    xtype: 'ldk-linkbutton',
                    text: 'Import Stims',
                    href: 'javascript:void(0);',
                    linkCls: 'labkey-text-link',
                    handler: function(btn){
                        if (LABKEY.Security.currentContainer.type === 'workbook'){
                            Ext4.define('TCRdb.window.StimUploadWindow', {
                                extend: 'Ext.window.Window',
                                initComponent: function(){
                                    Ext4.apply(this, {
                                        title: 'Import Stims',
                                        items: [{
                                            xtype: 'labkey-exceluploadpanel',
                                            bubbleEvents: ['uploadexception', 'uploadcomplete'],
                                            itemId: 'theForm',
                                            title: null,
                                            buttons: null,
                                            containerPath: Laboratory.Utils.getQueryContainerPath(),
                                            schemaName: 'tcrdb',
                                            queryName: 'stims',
                                            populateTemplates: function(meta){
                                                Ext4.Msg.hide();
                                                var toAdd = [];

                                                toAdd.push({
                                                    html: 'Use the button below to download an excel template for uploading stims.',
                                                    border: false,
                                                    style: 'padding-bottom: 10px;',
                                                    width: 700
                                                });

                                                toAdd.push({
                                                    xtype: 'ldk-integerfield',
                                                    itemId: 'templateRows',
                                                    fieldLabel: 'Total Stims',
                                                    labelWidth: 120,
                                                    value: 10
                                                });

                                                toAdd.push({
                                                    xtype: 'textfield',
                                                    itemId: 'treatment',
                                                    fieldLabel: 'Treatment',
                                                    labelWidth: 120,
                                                    value: 'TAPI-0'
                                                });

                                                toAdd.push({
                                                    xtype: 'textfield',
                                                    itemId: 'coStim',
                                                    fieldLabel: 'Co-Stim',
                                                    labelWidth: 120,
                                                    value: 'CD28/CD49d'
                                                });

                                                toAdd.push({
                                                    xtype: 'textfield',
                                                    itemId: 'effectors',
                                                    fieldLabel: 'Effector',
                                                    labelWidth: 120,
                                                    value: 'PBMC'
                                                });

                                                toAdd.push({
                                                    xtype: 'ldk-numberfield',
                                                    itemId: 'numEffectors',
                                                    fieldLabel: '# Effectors',
                                                    labelWidth: 120,
                                                    value: 1000000
                                                });

                                                toAdd.push({
                                                    xtype: 'textfield',
                                                    itemId: 'apc',
                                                    fieldLabel: 'APCs',
                                                    labelWidth: 120,
                                                    value: 'PBMC'
                                                });

                                                toAdd.push({
                                                    xtype: 'ldk-numberfield',
                                                    itemId: 'numAPC',
                                                    fieldLabel: '# APCs',
                                                    labelWidth: 120,
                                                    value: null
                                                });

                                                toAdd.push({
                                                    xtype: 'button',
                                                    style: 'margin-bottom: 10px;',
                                                    text: 'Download Template',
                                                    border: true,
                                                    handler: this.generateExcelTemplate
                                                });

                                                this.down('#templateArea').add(toAdd);
                                            },
                                            generateExcelTemplate: function(){
                                                var win = this.up('window');
                                                var numRows = win.down('#templateRows').getValue() || 1;
                                                var effectors = win.down('#effectors').getValue();
                                                var numEffectors = win.down('#numEffectors').getValue();
                                                var apc = win.down('#apc').getValue();
                                                var numAPC = win.down('#numAPC').getValue();
                                                var treatment = win.down('#treatment').getValue();
                                                var coStim = win.down('#coStim').getValue();

                                                var data = [];
                                                data.push(['Tube #', 'Animal/Cell', 'Sample Date', 'Effectors', '# Effectors', 'APCs', '# APCs', 'Treatment', 'Co-stim', 'Peptide/Stim', 'Comment']);
                                                for (var i=0;i<numRows;i++){
                                                    data.push([i+1, null, null, effectors, numEffectors, apc, numAPC, treatment, coStim, null]);
                                                }

                                                LABKEY.Utils.convertToExcel({
                                                    fileName: 'StimImport_' + Ext4.Date.format(new Date(), 'Y-m-d H_i_s') + '.xls',
                                                    sheets: [{
                                                        name: 'Stims',
                                                        data: data
                                                    }]
                                                });
                                            },
                                            listeners: {
                                                uploadcomplete: function(panel, response){
                                                    Ext4.Msg.alert('Success', 'Upload Complete!', function(btn){
                                                        this.up('window').close();
                                                        location.reload();
                                                    }, this);
                                                }
                                            }
                                        }]
                                    });

                                    this.callParent();
                                },
                                buttons: [{
                                    text: 'Upload',
                                    width: 50,
                                    handler: function(btn){
                                        var form = btn.up('window').down('#theForm');
                                        form.formSubmit.call(form, btn);
                                    },
                                    scope: this,
                                    formBind: true
                                },{
                                    text: 'Close',
                                    width: 50,
                                    handler: function(btn){
                                        btn.up('window').close();
                                    }
                                }]
                            });

                            Ext4.create('TCRdb.window.StimUploadWindow', {
                                stimRows: this.up('tcrdb-stimpanel').stimRows
                            }).show();
                        }
                        else {
                            Ext4.Msg.alert('Error', 'This is only allowed when in a specific workbook.  Please enter the workbook into the box at the top of the page and hit \'Go\'');
                        }
                    }
                }]
            },{
                title: 'Step 2: Sorts',
                defaults: {
                    border: false
                },
                items: [{
                    layout: {
                        type: 'table',
                        columns: 3,
                        tdAttrs: {style: 'padding-right: 10px;'}
                    },
                    defaults: {
                        border: false
                    },
                    items: [{
                        html: 'Total Sorts:'
                    }, {
                        html: '<a href="' + LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {
                            schemaName: 'tcrdb',
                            queryName: 'sorts'
                        }) + '">' + this.sortStats.totalSorts + '</a>'
                    }, {
                        xtype: 'ldk-linkbutton',
                        text: '(' + this.sortStats.totalPlates.length + ' plates)',
                        href: 'javascript:void(0);',
                        handler: this.getPlateCallback(this.sortStats.totalPlates)
                    }, {
                        html: 'Lacking cDNA Libraries (All):'
                    }, {
                        html: '<a href="' + LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {
                            schemaName: 'tcrdb',
                            queryName: 'sorts',
                            'query.numLibraries~eq': 0
                        }) + '">' + this.sortStats.lackingLibraries + '</a>'
                    }, {
                        xtype: 'ldk-linkbutton',
                        text: '(' + this.sortStats.totalPlatesLackingLibraries.length + ' plates)',
                        href: 'javascript:void(0);',
                        handler: this.getPlateCallback(this.sortStats.totalPlatesLackingLibraries)
                    }, {
                        html: 'Lacking cDNA Libraries (Bulk):'
                    }, {
                        html: '<a href="' + LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {
                            schemaName: 'tcrdb',
                            queryName: 'sorts',
                            'query.numLibraries~eq': 0,
                            'query.cells~gt': 1
                        }) + '">' + this.sortStats.bulkLackingLibraries + '</a>'
                    }, {
                        xtype: 'ldk-linkbutton',
                        text: '(' + this.sortStats.totalBulkPlatesLackingLibraries.length + ' plates)',
                        href: 'javascript:void(0);',
                        handler: this.getPlateCallback(this.sortStats.totalBulkPlatesLackingLibraries)
                    }]
                },{
                    xtype: 'ldk-linkbutton',
                    text: 'Import Sort Data For Stims',
                    href: 'javascript:void(0);',
                    linkCls: 'labkey-text-link',
                    handler: function (btn) {
                        if (LABKEY.Security.currentContainer.type === 'workbook') {
                            Ext4.define('TCRdb.window.SortUploadWindow', {
                                extend: 'Ext.window.Window',
                                initComponent: function () {
                                    Ext4.apply(this, {
                                        title: 'Import Sorts for Stims',
                                        items: [{
                                            xtype: 'labkey-exceluploadpanel',
                                            bubbleEvents: ['uploadexception', 'uploadcomplete'],
                                            itemId: 'theForm',
                                            title: null,
                                            buttons: null,
                                            containerPath: Laboratory.Utils.getQueryContainerPath(),
                                            schemaName: 'tcrdb',
                                            queryName: 'sorts',
                                            populateTemplates: function (meta) {
                                                Ext4.Msg.hide();
                                                var toAdd = [];

                                                toAdd.push({
                                                    html: 'Use the button below to download an excel template pre-populated with data from the sorts imported into this workbook.',
                                                    border: false,
                                                    style: 'padding-bottom: 10px;',
                                                    width: 700
                                                });

                                                toAdd.push({
                                                    xtype: 'textfield',
                                                    itemId: 'buffer',
                                                    fieldLabel: 'Sort Buffer',
                                                    labelWidth: 120,
                                                    value: 'QIAGEN TCL'
                                                });

                                                toAdd.push({
                                                    xtype: 'checkbox',
                                                    itemId: 'skipWithData',
                                                    fieldLabel: 'Skip Stims With Sorts Imported',
                                                    labelWidth: 120,
                                                    helpPopup: 'If checked, stims with sort records already importd will be skipped',
                                                    checked: true
                                                });

                                                toAdd.push({
                                                    xtype: 'ldk-integerfield',
                                                    itemId: 'templateRows',
                                                    fieldLabel: 'Rows Per Stim',
                                                    labelWidth: 120,
                                                    helpPopup: 'For each stim, the template will include this many rows',
                                                    value: 2
                                                });

                                                toAdd.push({
                                                    xtype: 'button',
                                                    style: 'margin-bottom: 10px;',
                                                    text: 'Download Template',
                                                    border: true,
                                                    handler: this.generateExcelTemplate
                                                });

                                                this.down('#templateArea').add(toAdd);
                                            },
                                            generateExcelTemplate: function () {
                                                var win = this.up('window');
                                                var rowsPer = win.down('#templateRows').getValue() || 1;
                                                var skipWithData = win.down('#skipWithData').getValue();
                                                var buffer = win.down('#buffer').getValue();

                                                var data = [];
                                                data.push(['TubeNum', 'StimId', 'AnimalId', 'SampleDate', 'Peptide/Stim', 'Treatment', 'Buffer', 'Population', 'Replicate', 'Cells', 'PlateId', 'Well', 'Comment']);
                                                Ext4.Array.forEach(win.stimRows, function (r) {
                                                    if (skipWithData && r.numSorts) {
                                                        return;
                                                    }

                                                    for (var i = 0; i < rowsPer; i++) {
                                                        data.push([r.tubeNum, r.rowid, r.animalId, r.date, r.stim, r.treatment, buffer, null, null, null, null, null, null, null]);
                                                    }
                                                }, this);

                                                LABKEY.Utils.convertToExcel({
                                                    fileName: 'SortImport_' + Ext4.Date.format(new Date(), 'Y-m-d H_i_s') + '.xls',
                                                    sheets: [{
                                                        name: 'Sorts',
                                                        data: data
                                                    }]
                                                });
                                            },
                                            listeners: {
                                                uploadcomplete: function (panel, response) {
                                                    Ext4.Msg.alert('Success', 'Upload Complete!', function (btn) {
                                                        this.up('window').close();
                                                        location.reload();
                                                    }, this);
                                                }
                                            }
                                        }]
                                    });

                                    this.callParent();
                                },
                                buttons: [{
                                    text: 'Upload',
                                    width: 50,
                                    handler: function (btn) {
                                        var form = btn.up('window').down('#theForm');
                                        form.formSubmit.call(form, btn);
                                    },
                                    scope: this,
                                    formBind: true
                                }, {
                                    text: 'Close',
                                    width: 50,
                                    handler: function (btn) {
                                        btn.up('window').close();
                                    }
                                }]
                            });

                            Ext4.create('TCRdb.window.SortUploadWindow', {
                                stimRows: this.up('tcrdb-stimpanel').stimRows
                            }).show();
                        }
                        else {
                            Ext4.Msg.alert('Error', 'This is only allowed when in a specific workbook.  Please enter the workbook into the box at the top of the page and hit \'Go\'');
                        }
                    }
                },{
                    xtype: 'ldk-linkbutton',
                    linkCls: 'labkey-text-link',
                    text: 'Update Requested Processing',
                    width: 600,
                    scope: this,
                    handler: function(){
                        var plates = [];
                        Ext4.Array.forEach(this.sortStats.totalPlates, function(p){
                            plates.push([p]);
                        }, this);

                        Ext4.create('Ext.window.Window', {
                            title: 'Update Requested Processing For Plates',
                            bodyStyle: 'padding: 5px;',
                            items: [{
                                xtype: 'combo',
                                width: 500,
                                fieldLabel: 'Processing',
                                itemId: 'processing',
                                multiSelect: true,
                                displayField: 'value',
                                valueField: 'value',
                                forceSelection: true,
                                store: {
                                    type: 'array',
                                    fields: ['value'],
                                    data: [['Whole Transcriptome RNA-Seq'], ['TCR Enrichment'], ['Archive Only']]
                                }
                            },{
                                xtype: 'combo',
                                width: 500,
                                multiSelect: true,
                                fieldLabel: 'Plates',
                                itemId: 'plates',
                                displayField: 'value',
                                valueField: 'value',
                                forceSelection: true,
                                store: {
                                    type: 'array',
                                    fields: ['value'],
                                    data: plates
                                }
                            }],
                            buttons: [{
                                text: 'Submit',
                                scope: this,
                                handler: function(btn){
                                    var processing = btn.up('window').down('#processing').getValue();
                                    var plates = btn.up('window').down('#plates').getValue();
                                    if (Ext4.isEmpty(processing) || Ext4.isEmpty(plates)){
                                        Ext4.Msg.alert('Error', 'Must provide the plate and processing needed');
                                        return;
                                    }

                                    var toInsert = [];
                                    Ext4.Array.forEach(plates, function(plate){
                                        Ext4.Array.forEach(processing, function(type){
                                            toInsert.push({
                                                container: '',
                                                plateId: plate,
                                                type: type
                                            });
                                        }, this);
                                    }, this);

                                    Ext4.Array.forEach(toInsert, function(row){
                                        Ext4.Array.forEach(this.sortRows, function(sr){
                                            if (sr.plateId == row.plateId){
                                                row.container = sr.container;
                                                return false;
                                            }
                                        }, this);
                                    }, this);

                                    btn.up('window').close();
                                    Ext4.Msg.wait('Saving...');
                                    LABKEY.Query.insertRows({
                                        schemaName: 'tcrdb',
                                        queryName: 'plate_processing',
                                        rows: toInsert,
                                        failure: LDK.Utils.getErrorCallback(),
                                        success: function(){
                                            Ext4.Msg.hide();
                                            Ext4.Msg.alert('Success', 'Rows inserted');
                                        },
                                        scope: this
                                    })

                                }
                            },{
                                text: 'Cancel',
                                handler: function(btn){
                                    btn.up('window').close();
                                }
                            }]
                        }).show();
                    }
                }]
            },{
                title: 'Step 3: cDNA Synthesis / Library Prep',
                defaults: {
                    border: false
                },
                items: [{
                    layout: {
                        type: 'table',
                        columns: 3,
                        tdAttrs: { style: 'padding-right: 10px;' }
                    },
                    defaults: {
                        border: false
                    },
                    items: [{
                        html: 'Total cDNA Libraries:'
                    },{
                        html: '<a href="' + LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {
                            schemaName: 'tcrdb',
                            queryName: 'cdnas'
                        }) + '">' + this.libraryStats.totalLibraries + '</a>'
                    },{
                        xtype: 'ldk-linkbutton',
                        text: '(' + this.libraryStats.totalPlates.length + ' plates)',
                        href: 'javascript:void(0);',
                        handler: this.getPlateCallback(this.libraryStats.totalPlates)
                    },{
                        html: 'Lacking Any Readset:'
                    },{
                        html: '<a href="' + LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'tcrdb', queryName: 'cdnas', 'query.readsetId~isblank': null, 'query.enrichedReadsetId~isblank': null}) + '">' + this.libraryStats.lackingAnyReadset + '</a>'
                    },{
                        xtype: 'ldk-linkbutton',
                        text: '(' + this.libraryStats.totalPlatesLackingAnyReadset.length + ' plates)',
                        href: 'javascript:void(0);',
                        handler: this.getPlateCallback(this.libraryStats.totalPlatesLackingAnyReadset)
                    },{
                        html: 'With Whole Transcriptome Readset:'
                    },{
                        html: '<a href="' + LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'tcrdb', queryName: 'cdnas', 'query.readsetId~isnonblank': null}) + '">' + this.libraryStats.withReadset + '</a>'
                    },{
                        xtype: 'ldk-linkbutton',
                        text: '(' + this.libraryStats.totalPlatesWithReadset.length + ' plates)',
                        href: 'javascript:void(0);',
                        handler: this.getPlateCallback(this.libraryStats.totalPlatesWithReadset)
                    },{
                        html: 'With TCR Enriched Readset:'
                    },{
                        html: '<a href="' + LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'tcrdb', queryName: 'cdnas', 'query.enrichedReadsetId~isnonblank': null}) + '">' + this.libraryStats.withTCRReadset + '</a>'
                    },{
                        xtype: 'ldk-linkbutton',
                        text: '(' + this.libraryStats.totalPlatesWithTCRReadset.length + ' plates)',
                        href: 'javascript:void(0);',
                        handler: this.getPlateCallback(this.libraryStats.totalPlatesWithTCRReadset)
                    }]
                },{
                    xtype: 'ldk-linkbutton',
                    text: 'Create cDNA Libraries From Sorts',
                    href: 'javascript:void(0);',
                    scope: this,
                    linkCls: 'labkey-text-link',
                    handler: function(){
                        if (LABKEY.Security.currentContainer.type === 'workbook'){
                            Ext4.define('TCRdb.window.cDNAUploadWindow', {
                                extend: 'Ext.window.Window',
                                initComponent: function(){
                                    Ext4.apply(this, {
                                        title: 'Create cDNA Libraries From Sorts',
                                        items: [{
                                            xtype: 'labkey-exceluploadpanel',
                                            bubbleEvents: ['uploadexception', 'uploadcomplete'],
                                            itemId: 'theForm',
                                            title: null,
                                            buttons: null,
                                            containerPath: Laboratory.Utils.getQueryContainerPath(),
                                            schemaName: 'tcrdb',
                                            queryName: 'cdnas',
                                            populateTemplates: function(meta){
                                                Ext4.Msg.hide();
                                                var toAdd = [];

                                                toAdd.push({
                                                    html: 'Use the button below to download an excel template pre-populated with data from the selected plate IDs.',
                                                    border: false,
                                                    style: 'padding-bottom: 10px;',
                                                    width: 700
                                                });

                                                toAdd.push({
                                                    xtype: 'textfield',
                                                    itemId: 'destPlate',
                                                    fieldLabel: 'Destination Plate ID',
                                                    labelWidth: 160
                                                });

                                                toAdd.push({
                                                    xtype: 'ldk-simplecombo',
                                                    itemId: 'chemistry',
                                                    fieldLabel: 'Chemistry',
                                                    labelWidth: 160,
                                                    storeValues: ['SMART-Seq2', 'Takara SMART-Seq HT'],
                                                    value: 'SMART-Seq2'
                                                });

                                                toAdd.push({
                                                    xtype: 'textarea',
                                                    itemId: 'plates',
                                                    fieldLabel: 'Source Plates',
                                                    labelWidth: 160,
                                                    //width: 200,
                                                    height: 100
                                                });

                                                var win = this.up('window');
                                                toAdd.push({
                                                    xtype: 'ldk-linkbutton',
                                                    itemId: 'showIds',
                                                    style: 'margin-left: 165px;',
                                                    text: 'Show Plate IDs',
                                                    scope: this,
                                                    handler: win.getPlateCallback(win.sortStats.totalPlatesLackingLibraries, 'plates'),
                                                    linkCls: 'labkey-text-link'
                                                });

                                                toAdd.push({
                                                    xtype: 'button',
                                                    style: 'margin-bottom: 10px;',
                                                    text: 'Download Template',
                                                    border: true,
                                                    handler: this.generateExcelTemplate
                                                });

                                                toAdd.push({
                                                    xtype: 'checkbox',
                                                    itemId: 'keepWell',
                                                    checked: true,
                                                    fieldLabel: 'Keep Original Well',
                                                    labelWidth: 160
                                                });

                                                this.down('#templateArea').add(toAdd);
                                            },

                                            generateExcelTemplate: function(btn) {
                                                var win = btn.up('window');
                                                var chemistry = win.down('#chemistry').getValue();
                                                var destPlate = win.down('#destPlate').getValue();
                                                var keepWell = win.down('#keepWell').getValue();

                                                if (!destPlate) {
                                                    Ext4.Msg.alert('Error', 'Must provide destination plate IDs');
                                                    return;
                                                }

                                                var plates = Ext4.String.trim(btn.up('window').down('textarea').getValue());
                                                if (!plates) {
                                                    Ext4.Msg.alert('Error', 'Must provide source plate IDs');
                                                    return;
                                                }

                                                plates = plates.replace(/[\r\n]+/g, '\n');
                                                plates = plates.replace(/[\n]+/g, '\n');
                                                plates = Ext4.String.trim(plates);
                                                if (plates){
                                                    plates = plates.split('\n');
                                                }

                                                Ext4.Msg.wait('Loading...');
                                                LABKEY.Query.selectRows({
                                                    containerPath: Laboratory.Utils.getQueryContainerPath(),
                                                    schemaName: 'tcrdb',
                                                    queryName: 'sorts',
                                                    sort: 'well/addressByColumn',
                                                    columns: 'rowid,stimId,stimId/animalId,stimId/effector,stimId/date,stimId/treatment,population,replicate,cells,plateId,well,well/addressByColumn,numLibraries',
                                                    scope: win,
                                                    filterArray: [LABKEY.Filter.create('plateId', plates.join(';'), LABKEY.Filter.Types.IN)],
                                                    failure: LDK.Utils.getErrorCallback(),
                                                    success: function (results) {
                                                        Ext4.Msg.hide();

                                                        if (!results || !results.rows || !results.rows.length) {
                                                            Ext4.Msg.alert('Error', 'No sorts found for the selected plates');
                                                            return;
                                                        }

                                                        var data = [];
                                                        data.push(['Source Plate', 'Source Well', 'SortId', 'Plate Id', 'Well', 'Name', 'Chemistry', 'Comments']);
                                                        var wellIdx = 0;
                                                        var wellsUsed = {};
                                                        var errors = [];
                                                        Ext4.Array.forEach(plates, function (sourcePlateId) {
                                                            Ext4.Array.forEach(results.rows, function (r) {
                                                                if (r.plateId !== sourcePlateId){
                                                                    return;
                                                                }

                                                                var name = TCRdb.panel.StimPanel.getNameFromSort(r);
                                                                var targetWell = keepWell ? r.well : this.wellNames96[wellIdx];
                                                                if (wellsUsed[targetWell]){
                                                                    errors.push('Duplicate well: ' + targetWell);
                                                                }
                                                                wellsUsed[targetWell] = true;
                                                                data.push([r.plateId, r.well, r.rowid, destPlate, targetWell, name, chemistry, null]);
                                                                wellIdx++;
                                                            }, this);
                                                        }, this);

                                                        for (var i=0;i<this.wellNames96.length;i++){
                                                            if (!wellsUsed[this.wellNames96[i]]){
                                                                data.push([null, null, null, destPlate, this.wellNames96[i], null, null, null]);
                                                            }
                                                        }

                                                        //sort by well idx
                                                        if (keepWell){
                                                            data.sort(this.getWellSort(this.wellNames96));
                                                        }

                                                        if (errors.length){
                                                            errors = Ext4.unique(errors);
                                                            Ext4.Msg.alert('Error', errors.join('<br>'), function(){
                                                                LABKEY.Utils.convertToExcel({
                                                                    fileName: 'cDNAImport_' + Ext4.Date.format(new Date(), 'Y-m-d H_i_s') + '.xls',
                                                                    sheets: [{
                                                                        name: 'cDNA Libraries',
                                                                        data: data
                                                                    }]
                                                                });
                                                            }, this);
                                                        }
                                                        else {
                                                            LABKEY.Utils.convertToExcel({
                                                                fileName: 'cDNAImport_' + Ext4.Date.format(new Date(), 'Y-m-d H_i_s') + '.xls',
                                                                sheets: [{
                                                                    name: 'cDNA Libraries',
                                                                    data: data
                                                                }]
                                                            });
                                                        }
                                                    }
                                                });
                                            },
                                            listeners: {
                                                uploadcomplete: function(panel, response){
                                                    Ext4.Msg.alert('Success', 'Upload Complete!', function(btn){
                                                        this.up('window').close();
                                                        location.reload();
                                                    }, this);
                                                }
                                            }
                                        }]
                                    });

                                    this.callParent();
                                },
                                getWellSort: function(wellNames96){
                                    return function(a, b){
                                        var idx1 = wellNames96.indexOf(a[4]);
                                        var idx2 = wellNames96.indexOf(b[4]);

                                        return idx1 - idx2;
                                    }
                                },
                                buttons: [{
                                    text: 'Upload',
                                    width: 50,
                                    handler: function(btn){
                                        var form = btn.up('window').down('#theForm');
                                        form.formSubmit.call(form, btn);
                                    },
                                    scope: this,
                                    formBind: true
                                },{
                                    text: 'Close',
                                    width: 50,
                                    handler: function(btn){
                                        btn.up('window').close();
                                    }
                                }]
                            });

                            Ext4.create('TCRdb.window.cDNAUploadWindow', {
                                wellNames96: this.wellNames96,
                                sortStats: this.sortStats,
                                getPlateCallback: this.getPlateCallback
                            }).show();
                        }
                        else {
                            Ext4.Msg.alert('Error', 'This is only allowed when in a specific workbook.  Please enter the workbook into the box at the top of the page and hit \'Go\'');
                        }
                    }
                },{
                    xtype: 'ldk-linkbutton',
                    text: 'Download Library Prep Template (box)',
                    linkCls: 'labkey-text-link',
                    href: 'https://ohsu.box.com/s/6kncrzm4ba9mxjput12v8u500tjlsip7',
                    linkTarget: '_blank'
                },{
                    xtype: 'ldk-linkbutton',
                    text: 'Download TCR Enrichment Library Prep Template (box)',
                    linkCls: 'labkey-text-link',
                    href: 'https://ohsu.box.com/s/js55a347q5mioqxwowe1dk3prkvn4d29',
                    linkTarget: '_blank'
                },{
                    xtype: 'ldk-linkbutton',
                    text: 'Download Names To Use In Protocols',
                    href: 'javascript:void(0);',
                    linkCls: 'labkey-text-link',
                    handler: function(){
                        if (LABKEY.Security.currentContainer.type === 'workbook'){
                            Ext4.Msg.wait('Loading...');
                            LABKEY.Query.selectRows({
                                containerPath: Laboratory.Utils.getQueryContainerPath(),
                                schemaName: 'tcrdb',
                                queryName: 'cdnas',
                                sort: 'well/addressByColumn',
                                scope: this,
                                failure: LDK.Utils.getErrorCallback(),
                                success: function(results){
                                    Ext4.Msg.hide();
                                    if (!results || !results.rows || !results.rows.length){
                                        Ext4.Msg.alert('Error', 'No cDNA libraries found');
                                        return;
                                    }

                                    var rows = [];
                                    rows.push(['Well', 'Name'].join('\t'));
                                    Ext4.Array.forEach(results.rows, function(r){
                                        var name = TCRdb.panel.StimPanel.getNameFromCDNAs(r);
                                        rows.push([r.well, name].join('\t'));
                                    }, this);

                                    Ext4.create('Ext.window.Window', {
                                        bodyStyle: 'padding: 5px;',
                                        items: [{
                                            html: 'Please use the following as names for the sorts in the folder',
                                            border: false,
                                            style: 'padding-bottom: 10px;'
                                        },{
                                            xtype: 'textarea',
                                            width: 500,
                                            height: 200,
                                            value: rows.join('\n')
                                        }],
                                        buttons: [{
                                            text: 'Close',
                                            handler: function(btn){
                                                btn.up('window').close();
                                            }
                                        }]
                                    }).show();
                                }
                            });
                        }
                        else {
                            Ext4.Msg.alert('Error', 'This is only allowed when in a specific workbook.  Please enter the workbook into the box at the top of the page and hit \'Go\'');
                        }
                    }
                }]
            },{
                title: 'Step 4: Create Readsets / Template for Sequencing',
                defaults: {
                    border: false
                },
                items: [{
                    layout: {
                        type: 'table',
                        columns: 2,
                        tdAttrs: { style: 'padding-right: 10px;' }
                    },
                    defaults: {
                        border: false
                    },
                    items: [{
                        html: 'Total Readsets:'
                    },{
                        html:  '<a href="' + LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'sequenceanalysis', queryName: 'sequence_readsets'}) + '">' + this.readsetStats.totalReadsets + '</a>'
                    },{
                        html: 'Data Not Imported:'
                    },{
                        html: '<a href="' + LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'sequenceanalysis', queryName: 'sequence_readsets', 'query.totalFiles~eq': 0}) + '">' + this.readsetStats.lackingData + '</a>'
                    }]
                },{
                    xtype: 'ldk-linkbutton',
                    text: 'Create Readsets From cDNA Libraries',
                    href: 'javascript:void(0);',
                    linkCls: 'labkey-text-link',
                    handler: function(){
                        if (LABKEY.Security.currentContainer.type === 'workbook'){
                            Ext4.define('TCRdb.window.ReadsetUploadWindow', {
                                extend: 'Ext.window.Window',
                                initComponent: function(){
                                    Ext4.apply(this, {
                                        title: 'Create Readsets From cDNAs',
                                        bodyStyle: 'padding: 5px;',
                                        items: [{
                                            html: 'Use the button below to download an excel template pre-populated with data from the sorts imported into this workbook.',
                                            border: false,
                                            style: 'padding-bottom: 10px;',
                                            width: 700
                                        },{
                                            xtype: 'textarea',
                                            itemId: 'plateIds',
                                            fieldLabel: 'Plate Id(s)',
                                            labelWidth: 120,
                                            height: 100
                                        },{
                                            xtype: 'ldk-linkbutton',
                                            itemId: 'showIds',
                                            text: 'Show Plate IDs',
                                            style: 'margin-left: 125px;',
                                            scope: this,
                                            handler: this.getPlateCallback(this.libraryStats.totalPlatesLackingAnyReadset, 'plateIds'),
                                            linkCls: 'labkey-text-link'
                                        },{
                                            xtype: 'ldk-simplecombo',
                                            itemId: 'application',
                                            fieldLabel: 'Application',
                                            labelWidth: 120,
                                            storeValues: ['Whole Transcriptome RNA-Seq', 'TCR Enrichment', 'Both'],
                                            forceSelection: true
                                        },{
                                            xtype: 'labkey-combo',
                                            itemId: 'chemistry',
                                            fieldLabel: 'Chemistry',
                                            labelWidth: 120,
                                            store: {
                                                type: 'labkey-store',
                                                schemaName: 'sequenceanalysis',
                                                queryName: 'sequence_chemistries',
                                                autoLoad: true
                                            },
                                            displayField: 'chemistry',
                                            valueField: 'chemistry',
                                            value: null,
                                            forceSelection: true
                                        },{
                                            xtype: 'checkbox',
                                            itemId: 'includeImported',
                                            fieldLabel: 'Include Those With Existing Readsets'
                                        },{
                                            xtype: 'button',
                                            style: 'margin-bottom: 10px;',
                                            text: 'Download Template',
                                            border: true,
                                            handler: this.generateExcelTemplate
                                        },{
                                            xtype: 'textarea',
                                            height: 350,
                                            width: 700,
                                            itemId: 'template'
                                        }]
                                    });

                                    this.callParent();
                                },
                                generateExcelTemplate: function(){
                                    var win = this.up('window');

                                    //'Whole Transcriptome RNA-Seq', 'TCR Enrichment', 'Both'
                                    var type = win.down('#application').getValue();
                                    var chemistry = win.down('#chemistry').getValue();
                                    var includeImported = win.down('#includeImported').getValue();
                                    var plates = Ext4.String.trim(win.down('textarea').getValue());
                                    if (!plates) {
                                        Ext4.Msg.alert('Error', 'Must provide source plate IDs');
                                        return;
                                    }

                                    plates = plates.replace(/[\r\n]+/g, '\n');
                                    plates = plates.replace(/[\n]+/g, '\n');
                                    plates = Ext4.String.trim(plates);
                                    if (plates){
                                        plates = plates.split('\n');
                                    }

                                    if (!type){
                                        Ext4.Msg.alert('Error', 'Must choose the readset type');
                                        return;
                                    }

                                    var applications = [];
                                    switch (type) {
                                        case 'Whole Transcriptome RNA-Seq':
                                            applications.push('RNA-seq');
                                            break;
                                        case 'TCR Enrichment':
                                            applications.push('RNA-seq + Enrichment');
                                            break;
                                        case 'Both':
                                            applications.push('RNA-seq');
                                            applications.push('RNA-seq + Enrichment');
                                    }

                                    var data = [];
                                    data.push(['LibraryId', 'PlateId', 'Source Well', 'Name', 'Subject Id', 'Sample Date', '5-Barcode', '3-Barcode', 'Sample Type', 'Sequencing Platform', 'Application', 'Chemistry', 'Comments']);
                                    Ext4.Array.forEach(win.libraryRows, function(r){
                                        Ext4.Array.forEach(applications, function(application){
                                            if (plates.indexOf(r.plateId) > -1) {
                                                if (includeImported || (application === 'RNA-seq + Enrichment' && !r.enrichedReadsetId) || (application === 'RNA-seq' && !r.readsetId)) {
                                                    if (application === 'RNA-seq' && r.cells === 1) {
                                                        application = 'RNA-seq, Single Cell';
                                                    }

                                                    var name = TCRdb.panel.StimPanel.getNameFromCDNAs(r);
                                                    data.push([r.rowid, r.plateId, r.well, name, r['sortId/stimId/animalId'], r['sortId/stimId/date'], null, null, 'mRNA', 'ILLUMINA', application, chemistry, null]);
                                                }
                                            }
                                        }, this);
                                    }, this);

                                    if (data.length == 1){
                                        Ext4.Msg.alert('Error', 'No matching rows found');
                                        return;
                                    }

                                    LABKEY.Utils.convertToExcel({
                                        fileName: 'ReadsetImport_' + Ext4.Date.format(new Date(), 'Y-m-d H_i_s') + '.xls',
                                        sheets: [{
                                            name: 'Readsets',
                                            data: data
                                        }]
                                    });
                                },
                                buttons: [{
                                    text: 'Upload',
                                    width: 50,
                                    handler: function(btn){
                                        btn.up('window').processUpload();
                                    },
                                    scope: this,
                                    formBind: true
                                },{
                                    text: 'Close',
                                    width: 50,
                                    handler: function(btn){
                                        btn.up('window').close();
                                    }
                                }],
                                processUpload: function(){
                                    var text = this.down('#template').getValue();
                                    if (!text){
                                        Ext4.Msg.alert('Error', 'No rows provided');
                                        return;
                                    }
                                    text = LDK.Utils.CSVToArray(Ext4.String.trim(text), '\t');

                                    var header = text.shift();
                                    var headerToField = {
                                        Name: 'name',
                                        'Subject Id': 'subjectid',
                                        'Sample Date': 'sampledate',
                                        '5-Barcode': 'barcode5',
                                        '3-Barcode': 'barcode3',
                                        'Sample Type': 'sampletype',
                                        'Sequencing Platform': 'platform',
                                        'Application': 'application',
                                        'Chemistry': 'chemsitry',
                                        'Comments': 'comments',
                                        'LibraryId': 'libraryId'

                                    };

                                    var readsetToInsert = [];
                                    var cDNAsToUpdate = {};
                                    var errorMsgs = [];

                                    //TODO
                                    // var containerMap = {};
                                    // var win = this.up('window');
                                    // Ext4.Array.forEach(win.libraryRows, function(r){
                                    //     console.log(r);
                                    //     containerMap[r.rowId] = r.container;
                                    // }, this);

                                    Ext4.Array.forEach(text, function (row, rowIdx) {
                                        var r = {};
                                        for (var headerName in headerToField){
                                            var idx = header.indexOf(headerName);
                                            if (idx !== -1 && row.length > idx){
                                                r[headerToField[headerName]] = row[idx];
                                            }
                                        }

                                        if (!r.name || !r.application || !r.barcode5 || !r.barcode3 || !r.libraryId){
                                            errorMsgs.push('Every row must have name, application and forward/reverse barcodes');
                                        }

                                        //TODO: set container to match sort
                                        // if (row.libraryId && containerMap[row.libraryId]){
                                        //     row.container = containerMap[row.libraryId];
                                        // }
                                        //
                                        // if (!row.container){
                                        //     //TODO
                                        // }

                                        readsetToInsert.push(r);

                                        cDNAsToUpdate[r.libraryId] = cDNAsToUpdate[r.libraryId] || {};
                                        if (r.application.toLowerCase() === 'RNA-seq'.toLowerCase()){
                                            cDNAsToUpdate[r.libraryId].readsetIdx = rowIdx;
                                        }
                                        else if (r.application.toLowerCase() === 'RNA-seq + Enrichment'.toLowerCase()){
                                            cDNAsToUpdate[r.libraryId].enrichedReadsetIdx = rowIdx;
                                        }
                                        else {
                                            errorMsgs.push('Unknown application: ' + r.application);
                                        }
                                    }, this);

                                    if (errorMsgs.length){
                                        Ext4.Msg.alert('Error', errorMsgs.join('<br>'));
                                        return;
                                    }

                                    Ext4.Msg.wait('Saving...');
                                    LABKEY.Query.insertRows({
                                        //containerPath: Laboratory.Utils.getQueryContainerPath(),
                                        schemaName: 'sequenceanalysis',
                                        queryName: 'sequence_readsets',
                                        rows: readsetToInsert,
                                        scope: this,
                                        failure: LDK.Utils.getErrorCallback(),
                                        success: function (results) {
                                            var toUpdate = [];
                                            for (var libraryId in cDNAsToUpdate){
                                                //TODO: add container
                                                var r = {rowid: libraryId};
                                                if (Ext4.isDefined(cDNAsToUpdate[libraryId].readsetIdx)){
                                                    r.readsetId = results.rows[cDNAsToUpdate[libraryId].readsetIdx].rowId
                                                }

                                                if (Ext4.isDefined(cDNAsToUpdate[libraryId].enrichedReadsetIdx)){
                                                    r.enrichedReadsetId = results.rows[cDNAsToUpdate[libraryId].enrichedReadsetIdx].rowId
                                                }

                                                if (r.readsetId || r.enrichedReadsetId){
                                                    toUpdate.push(r);
                                                }
                                            }

                                            if (toUpdate.length){
                                                LABKEY.Query.updateRows({
                                                    //containerPath: Laboratory.Utils.getQueryContainerPath(),
                                                    schemaName: 'tcrdb',
                                                    queryName: 'cdnas',
                                                    rows: toUpdate,
                                                    scope: this,
                                                    failure: LDK.Utils.getErrorCallback(),
                                                    success: function (results) {
                                                        Ext4.Msg.hide();

                                                        Ext4.Msg.alert('Success', 'Rows saved', function(){
                                                            window.location.reload();
                                                        });
                                                    }
                                                });
                                            }
                                            else {
                                                Ext4.Msg.hide();
                                                Ext4.Msg.alert('Error', 'There were no readsets to update');
                                            }
                                        }
                                    });
                                }
                            });

                            Ext4.create('TCRdb.window.ReadsetUploadWindow', {
                                libraryRows: this.up('tcrdb-stimpanel').libraryRows,
                                libraryStats: this.up('tcrdb-stimpanel').libraryStats,
                                getPlateCallback: this.up('tcrdb-stimpanel').getPlateCallback
                            }).show();
                        }
                        else {
                            Ext4.Msg.alert('Error', 'This is only allowed when in a specific workbook.  Please enter the workbook into the box at the top of the page and hit \'Go\'');
                        }
                    }
                },{
                    xtype: 'ldk-linkbutton',
                    text: 'Download Blank MPSSR Template (box)',
                    linkCls: 'labkey-text-link',
                    href: 'https://ohsu.box.com/s/awhkmncp3gphs60inlu0mnts1yd22z25'
                },{
                    xtype: 'ldk-linkbutton',
                    text: 'Request Runs From MPSSR (iLABS)',
                    linkCls: 'labkey-text-link',
                    href: 'https://ohsu.corefacilities.org/account/pending/ohsu'
                },{
                    xtype: 'ldk-linkbutton',
                    text: 'MedGenome Information',
                    linkCls: 'labkey-text-link',
                    href: 'https://prime-seq.ohsu.edu/wiki/Internal/Bimber/page.view?name=tcrSequenceShipping'
                },{
                    xtype: 'ldk-linkbutton',
                    text: 'Download Readset Names and Barcodes For Core/Vendor',
                    href: 'javascript:void(0);',
                    linkCls: 'labkey-text-link',
                    scope: this,
                    handler: function(){
                        Ext4.create('Ext.window.Window', {
                            bodyStyle: 'padding: 5px;',
                            items: [{
                                xtype: 'ldk-simplecombo',
                                itemId: 'instrument',
                                fieldLabel: 'Instrument/Core',
                                forceSelection: true,
                                editable: false,
                                labelWidth: 160,
                                storeValues: ['NextSeq (MPSSR)', 'MiSeq (ONPRC)', 'Basic List (MedGenome)']
                            },{
                                xtype: 'ldk-simplecombo',
                                itemId: 'application',
                                fieldLabel: 'Application/Type',
                                forceSelection: true,
                                editable: true,
                                labelWidth: 160,
                                allowBlank: true,
                                storeValues: ['Whole Transcriptome RNA-Seq', 'TCR Enriched']
                            },{
                                xtype: 'labkey-combo',
                                forceSelection: true,
                                multiSelect: true,
                                displayField: 'plateId',
                                valueField: 'plateId',
                                itemId: 'sourcePlates',
                                fieldLabel: 'Source Plate Id',
                                store: {
                                    type: 'labkey-store',
                                    schemaName: 'tcrdb',
                                    sql: 'SELECT distinct plateId as plateId from tcrdb.cdnas c WHERE c.allReadsetsHaveData = false',
                                    autoLoad: true
                                },
                                labelWidth: 160
                            },{
                                xtype: 'textfield',
                                itemId: 'adapter',
                                fieldLabel: 'Adapter',
                                labelWidth: 160,
                                value: 'CTGTCTCTTATACACATCT'
                            },{
                                xtype: 'labkey-combo',
                                itemId: 'barcodes',
                                fieldLabel: 'Barcode Series',
                                labelWidth: 160,
                                store: {
                                    type: 'labkey-store',
                                    schemaName: 'sequenceanalysis',
                                    queryName: 'barcode_groups',
                                    autoLoad: true
                                },
                                displayField: 'group_name',
                                valueField: 'group_name',
                                value: 'Illumina',
                                forceSelection: true
                            },{
                                xtype: 'textarea',
                                fieldLabel: 'Names/Barcodes',
                                labelAlign: 'top',
                                width: 600,
                                height: 300
                            },{
                                xtype: 'checkbox',
                                fieldLabel: 'Include Libraries With Data',
                                checked: false,
                                itemId: 'includeWithData',
                                listeners: {
                                    change: function(field, val){
                                        var target = field.up('window').down('#sourcePlates');
                                        var sql = 'SELECT distinct plateId as plateId from tcrdb.cdnas ' + (val ? '' : 'c WHERE c.allReadsetsHaveData = false');
                                        target.store.sql = sql;
                                        target.store.removeAll();
                                        target.store.load(function(){
                                            if (target.getPicker()){
                                                target.getPicker().refresh();
                                            }
                                        }, this);
                                    }
                                }
                            },{
                                xtype: 'checkbox',
                                fieldLabel: 'Allow Duplicate Barcodes',
                                checked: false,
                                itemId: 'allowDuplicates'
                            }],
                            doReverseComplement: function(seq){
                                var match={'a': 'T', 'A': 'T', 't': 'A', 'T': 'A', 'g': 'C', 'G': 'C', 'c': 'G', 'C': 'G'};
                                var o = '';
                                for (var i = seq.length - 1; i >= 0; i--) {
                                    if (match[seq[i]] === undefined) break;
                                    o += match[seq[i]];
                                }

                                return o;
                            },
                            buttons: [{
                                text: 'Submit',
                                scope: this,
                                handler: function(btn){
                                    var barcodes = btn.up('window').down('#barcodes').getValue();
                                    var plateIds = btn.up('window').down('#sourcePlates').getValue();
                                    if (!plateIds || !plateIds.length){
                                        Ext4.Msg.alert('Error', 'Must provide the plate Id(s)');
                                        return;
                                    }

                                    var instrument = btn.up('window').down('#instrument').getValue();
                                    var application = btn.up('window').down('#application').getValue();
                                    var adapter = btn.up('window').down('#adapter').getValue();
                                    var includeWithData = btn.up('window').down('#includeWithData').getValue();
                                    var allowDuplicates = btn.up('window').down('#allowDuplicates').getValue();
                                    var doReverseComplement = btn.up('window').doReverseComplement;
                                    var isMatchingApplication = function(application, readsetApplication){
                                        if (!application){
                                            return true;
                                        }

                                        if (application == 'Whole Transcriptome RNA-Seq'){
                                            return readsetApplication === 'RNA-seq' || readsetApplication === 'RNA-seq, Single Cell';
                                        }
                                        else if (application == 'TCR Enriched'){
                                            return readsetApplication === 'RNA-seq + Enrichment';
                                        }
                                    };

                                    LABKEY.Query.selectRows({
                                        containerPath: Laboratory.Utils.getQueryContainerPath(),
                                        schemaName: 'tcrdb',
                                        queryName: 'cdnas',
                                        sort: 'plateId,well/addressByColumn',
                                        columns: 'rowid,readsetId,readsetId/name,readsetId/application,readsetId/barcode5,readsetId/barcode5/sequence,readsetId/barcode3,readsetId/barcode3/sequence,readsetId/totalFiles,enrichedReadsetId,enrichedReadsetId/name,enrichedReadsetId/application,enrichedReadsetId/barcode5,enrichedReadsetId/barcode5/sequence,enrichedReadsetId/barcode3,enrichedReadsetId/barcode3/sequence,enrichedReadsetId/totalFiles',
                                        scope: this,
                                        filterArray: [LABKEY.Filter.create('plateId', plateIds.join(';'), LABKEY.Filter.Types.IN)],
                                        failure: LDK.Utils.getErrorCallback(),
                                        success: function (results) {
                                            Ext4.Msg.hide();

                                            if (!results || !results.rows || !results.rows.length) {
                                                Ext4.Msg.alert('Error', 'No libraries found for the selected plates');
                                                return;
                                            }

                                            var barcodeCombosUsed = [];
                                            if (instrument == 'NextSeq (MPSSR)' || instrument == 'Basic List (MedGenome)') {
                                                var rc5 = (instrument == 'NextSeq (MPSSR)');
                                                var rc3 = (instrument == 'NextSeq (MPSSR)');

                                                var rows = [['Name', 'Adapter', 'I7_Index_ID', 'I7_Seq', 'I5_Index_ID', 'I5_Seq'].join('\t')];
                                                Ext4.Array.forEach(results.rows, function (r) {
                                                    //only include readsets without existing data
                                                    if (r.readsetId && (includeWithData || r['readsetId/totalFiles'] == 0) && isMatchingApplication(application, r['readsetId/application'])) {
                                                        //reverse complement both barcodes:
                                                        var barcode5 = rc5 ? doReverseComplement(r['readsetId/barcode5/sequence']) : r['readsetId/barcode5/sequence'];
                                                        var barcode3 = rc3 ? doReverseComplement(r['readsetId/barcode3/sequence']) : r['readsetId/barcode3/sequence'];
                                                        barcodeCombosUsed.push(r['readsetId/barcode5'] + '/' + r['readsetId/barcode3']);
                                                        rows.push([r.readsetId + '_' + r['readsetId/name'], adapter, r['readsetId/barcode5'], barcode5, r['readsetId/barcode3'], barcode3].join('\t'));
                                                    }

                                                    if (r.enrichedReadsetId && (includeWithData || r['enrichedReadsetId/totalFiles'] == 0) && isMatchingApplication(application, r['enrichedReadsetId/application'])) {
                                                        var barcode5 = rc5 ? doReverseComplement(r['enrichedReadsetId/barcode5/sequence']) : r['enrichedReadsetId/barcode5/sequence'];
                                                        var barcode3 = rc3 ? doReverseComplement(r['enrichedReadsetId/barcode3/sequence']) : r['enrichedReadsetId/barcode3/sequence'];
                                                        barcodeCombosUsed.push(r['enrichedReadsetId/barcode5'] + '/' + r['enrichedReadsetId/barcode3']);
                                                        rows.push([r.enrichedReadsetId + '_' + r['enrichedReadsetId/name'], adapter, r['enrichedReadsetId/barcode5'], barcode5, r['enrichedReadsetId/barcode3'], barcode3].join('\t'))
                                                    }
                                                }, this);

                                                //add missing barcodes:
                                                var blankIdx = 0;
                                                Ext4.Array.forEach(TCRdb.panel.StimPanel.BARCODES5, function(barcode5){
                                                    Ext4.Array.forEach(TCRdb.panel.StimPanel.BARCODES3, function(barcode3){
                                                        var combo = barcode5 + '/' + barcode3;
                                                        if (barcodeCombosUsed.indexOf(combo) == -1){
                                                            blankIdx++;
                                                            var barcode5Seq = rc5 ? doReverseComplement(this.barcodeMap[barcodes][barcode5]) : this.barcodeMap[barcodes][barcode5];
                                                            var barcode3Seq = rc3 ? doReverseComplement(this.barcodeMap[barcodes][barcode3]) : this.barcodeMap[barcodes][barcode3];
                                                            rows.push([plateIds.join(';') + '_Blank' + blankIdx, adapter, barcode5, barcode5Seq, barcode3, barcode3Seq].join('\t'));
                                                        }
                                                    }, this);
                                                }, this);
                                            }
                                            else {
                                                var rows = [];
                                                rows.push('[Header]');
                                                rows.push('IEMFileVersion,4');
                                                rows.push('Investigator Name,Bimber');
                                                rows.push('Experiment Name,' + plateIds.join(';'));
                                                rows.push('Date,11/16/2017');
                                                rows.push('Workflow,GenerateFASTQ');
                                                rows.push('Application,FASTQ Only');
                                                rows.push('Assay,Nextera XT');
                                                rows.push('Description,');
                                                rows.push('Chemistry,Amplicon');
                                                rows.push('');
                                                rows.push('[Reads]');
                                                rows.push('251');
                                                rows.push('251');
                                                rows.push('');
                                                rows.push('[Settings]');
                                                rows.push('ReverseComplement,0');
                                                rows.push('Adapter,' + adapter);
                                                rows.push('');
                                                rows.push('[Data]');
                                                rows.push('Sample_ID,Sample_Name,Sample_Plate,Sample_Well,I7_Index_ID,index,I5_Index_ID,index2,Sample_Project,Description');

                                                Ext4.Array.forEach(results.rows, function (r) {
                                                    //only include readsets without existing data
                                                    if (r.readsetId && (includeWithData || r['readsetId/totalFiles'] == 0) && isMatchingApplication(application, r['readsetId/application'])) {
                                                        //reverse complement both barcodes:
                                                        var barcode5 = doReverseComplement(r['readsetId/barcode5/sequence']);
                                                        var barcode3 = r['readsetId/barcode3/sequence'];
                                                        var cleanedName = r.readsetId + '_' + r['readsetId/name'].replace(/ /g, '_');
                                                        cleanedName = cleanedName.replace(/\//g, '-');

                                                        barcodeCombosUsed.push(r['readsetId/barcode5'] + '/' + r['readsetId/barcode3']);
                                                        rows.push([r.readsetId, cleanedName, '', '', r['readsetId/barcode5'], barcode5, r['readsetId/barcode3'], barcode3].join(','));
                                                    }

                                                    if (r.enrichedReadsetId && (includeWithData || r['enrichedReadsetId/totalFiles'] == 0) && isMatchingApplication(application, r['enrichedReadsetId/application'])) {
                                                        var barcode5 = doReverseComplement(r['enrichedReadsetId/barcode5/sequence']);
                                                        var barcode3 = r['enrichedReadsetId/barcode3/sequence'];
                                                        var cleanedName = r.enrichedReadsetId + '_' + r['enrichedReadsetId/name'].replace(/ /g, '_');
                                                        cleanedName = cleanedName.replace(/\//g, '-');

                                                        barcodeCombosUsed.push(r['enrichedReadsetId/barcode5'] + '/' + r['enrichedReadsetId/barcode3']);
                                                        rows.push([r.enrichedReadsetId, cleanedName, '', '', r['enrichedReadsetId/barcode5'], barcode5, r['enrichedReadsetId/barcode3'], barcode3].join(','))
                                                    }
                                                }, this);

                                                //add missing barcodes:
                                                var blankIdx = 0;
                                                Ext4.Array.forEach(TCRdb.panel.StimPanel.BARCODES5, function(barcode5){
                                                    Ext4.Array.forEach(TCRdb.panel.StimPanel.BARCODES3, function(barcode3){
                                                        var combo = barcode5 + '/' + barcode3;
                                                        if (barcodeCombosUsed.indexOf(combo) == -1){
                                                            blankIdx++;
                                                            var barcode5Seq = doReverseComplement(this.barcodeMap[barcodes][barcode5]);
                                                            var barcode3Seq = this.barcodeMap[barcodes][barcode3];
                                                            rows.push([plateIds.join(';') + '_Blank' + blankIdx, null, null, null, barcode5, barcode5Seq, barcode3, barcode3Seq].join(','));
                                                        }
                                                    }, this);
                                                }, this);
                                            }

                                            //check for unique barcodes
                                            var sorted = barcodeCombosUsed.slice().sort();
                                            var duplicates = [];
                                            for (var i = 0; i < sorted.length - 1; i++) {
                                                if (sorted[i + 1] == sorted[i]) {
                                                    duplicates.push(sorted[i]);
                                                }
                                            }

                                            duplicates = Ext4.unique(duplicates);
                                            if (!allowDuplicates && duplicates.length){
                                                Ext4.Msg.alert('Error', 'Duplicate barcodes: ' + duplicates.join(', '));
                                                btn.up('window').down('textarea').setValue(null);
                                                btn.up('window').down('#downloadData').setDisabled(true);
                                            }
                                            else {
                                                btn.up('window').down('textarea').setValue(rows.join('\n'));
                                                btn.up('window').down('#downloadData').setDisabled(false);
                                            }
                                        }
                                    });
                                }
                            },{
                                text: 'Download Data',
                                itemId: 'downloadData',
                                disabled: true,
                                handler: function(btn){
                                    var instrument = btn.up('window').down('#instrument').getValue();
                                    var plateId = btn.up('window').down('#sourcePlates').getValue();
                                    var delim = 'TAB';
                                    var extention = 'txt';
                                    var split = '\t';
                                    if (instrument != 'NextSeq (MPSSR)'){
                                        delim = 'COMMA';
                                        extention = 'csv';
                                        split = ',';
                                    }

                                    var val = btn.up('window').down('textarea').getValue();
                                    var rows = LDK.Utils.CSVToArray(Ext4.String.trim(val), split);

                                    LABKEY.Utils.convertToTable({
                                        fileName: plateId + '.' + extention,
                                        rows: rows,
                                        delim: delim
                                    });
                                }
                            },{
                                text: 'Close',
                                handler: function(btn){
                                    btn.up('window').close();
                                }
                            }]
                        }).show();
                    }
                }]
            },{
                title: 'Plate Summary',
                defaults: {
                    border: false
                },
                items: [{
                    xtype: 'ldk-linkbutton',
                    text: 'View Summary of Sorts By Plate (this workbook)',
                    linkCls: 'labkey-text-link',
                    hidden: LABKEY.Security.currentContainer.type != 'workbook',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'tcrdb', queryName: 'sortStatusByPlate', 'query.isComplete~eq': false})
                },{
                    xtype: 'ldk-linkbutton',
                    text: 'View Summary of Sorts By Plate (entire folder)',
                    linkCls: 'labkey-text-link',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', Laboratory.Utils.getQueryContainerPath(), {schemaName: 'tcrdb', queryName: 'sortStatusByPlate', 'query.isComplete~eq': false})
                },{
                    xtype: 'ldk-linkbutton',
                    text: 'View Summary of Sorts By Animal/Plate (this workbook)',
                    linkCls: 'labkey-text-link',
                    hidden: LABKEY.Security.currentContainer.type != 'workbook',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'tcrdb', queryName: 'sortStatusByPlateAndSample'})
                },{
                    xtype: 'ldk-linkbutton',
                    text: 'View Summary of Sorts By Animal/Plate (entire folder)',
                    linkCls: 'labkey-text-link',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', Laboratory.Utils.getQueryContainerPath(), {schemaName: 'tcrdb', queryName: 'sortStatusByPlateAndSample'})
                }]
            }]
        }
    },

    getPlateCallback: function(plateIds, fieldId){
        return function(f){
            var target;
            if (fieldId){
                target = f.up('window').down('#' + fieldId);
            }

            var items = [];
            Ext4.Array.forEach(plateIds, function(id){
                var listener = target ? {
                    scope: this,
                    afterrender: function(panel){
                        panel.mon(panel.getEl(), 'click', function(){
                            target.setValue(target.getValue() + (target.getValue() ? '\n' : '') + id);
                        }, this);
                    }
                } : null;
                items.push({
                    html: id,
                    bodyStyle: target ? 'text-decoration: underline;cursor: pointer;' : null,
                    border: false,
                    listeners: listener
                });
            }, this);

            if (plateIds.length == 0){
                items.push({html: 'There are no plates in this folder', border: false});
            }

            Ext4.create('Ext.window.Window', {
                modal: true,
                title: 'Plate IDs',
                maxHeight: '400',
                autoScroll: true,
                width: 300,
                bodyStyle: 'padding: 5px;',
                items: [{
                    xtype: 'container',
                    items: items
                }],
                buttons: [{
                    text: 'Close',
                    handler: function(btn){
                        btn.up('window').close();
                    }
                }]
            }).show();
        }
    },

    statics: {
        getNameFromSort: function(r){
            return [
                r['plateId'],
                r['well'],
                r['stimId/animalId'],
                r['stimId/stim'],
                r['stimId/treatment'],
                r.population + (r.replicate ? '_' + r.replicate : '')
            ].join('_').replace(/ /g, '-');
        },

        getNameFromCDNAs: function(r){
            return [
                //NOTE: preferentially retain the original sort plate name, in case of combined cDNA plates
                r['sortId/plateId'] || r['plateId'],
                r['well'],
                r['sortId/stimId/animalId'],
                r['sortId/stimId/stim'],
                r['sortId/stimId/treatment'],
                r['sortId/population'] + (r['sortId/cells'] === 1 ? '_Clone' : '') + (r['sortId/replicate'] ? '_' + r['sortId/replicate'] : '')
            ].join('_').replace(/ /g, '-').replace(/\(/g, '').replace(/\)/g, '').replace(/\+/g, 'Pos');
        },

        BARCODES5: ['N701', 'N702', 'N703', 'N704', 'N705', 'N706', 'N707', 'N708', 'N709', 'N710', 'N711', 'N712'],

        BARCODES3: ['S517', 'S502', 'S503', 'S504', 'S505', 'S506', 'S507', 'S508']
    }
});