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
                                                    value: 'Takara Buffer'
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
                                                    storeValues: ['SMART-Seq2', 'Takara SMART-Seq HT', '10x GEX/VDJ'],
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
                                                    columns: 'rowid,stimId,stimId/animalId,stimId/effector,stimId/stim,stimId/date,stimId/treatment,population,replicate,cells,plateId,well,well/addressByColumn,numLibraries',
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
                                            storeValues: ['Whole Transcriptome RNA-Seq', 'TCR Enrichment', '10x GEX Only', '10x GEX/TCR'],
                                            forceSelection: true,
                                            multiSelect: true
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
                                            value: 'Illumina HiSeq3000',
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

                                    //'Whole Transcriptome RNA-Seq', 'TCR Enrichment', 10x GEX Only, 10x GEX/TCR
                                    var types = win.down('#application').getValue();
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

                                    if (!types || !types.length){
                                        Ext4.Msg.alert('Error', 'Must choose the application(s)');
                                        return;
                                    }

                                    var applications = [];
                                    Ext4.Array.forEach(types, function(type) {
                                        switch (type) {
                                            case 'Whole Transcriptome RNA-Seq':
                                                applications.push('RNA-seq');
                                                break;
                                            case 'TCR Enrichment':
                                                applications.push('RNA-seq + Enrichment');
                                                break;
                                            case '10x GEX Only':
                                                applications.push('10x GEX');
                                                break;
                                            case '10x GEX/TCR':
                                                applications.push('10x GEX');
                                                applications.push('10x VDJ');
                                        }
                                    }, this);
                                    applications = Ext4.unique(applications);

                                    var data = [];
                                    data.push(['LibraryId', 'PlateId', 'Source Well', 'Name', 'Subject Id', 'Sample Date', '5-Barcode', '3-Barcode', 'Sample Type', 'Sequencing Platform', 'Application', 'Chemistry', 'Library Type', 'Comments']);
                                    Ext4.Array.forEach(win.libraryRows, function(r){
                                        Ext4.Array.forEach(applications, function(application){
                                            if (plates.indexOf(r.plateId) > -1) {
                                                if (includeImported || (['RNA-seq + Enrichment', '10x VDJ'].indexOf(application) > -1 && !r.enrichedReadsetId) || (['RNA-seq', '10x GEX'].indexOf(application) > -1 && !r.readsetId)) {
                                                    var applicationValue = application;
                                                    if (application === 'RNA-seq' && r.cells === 1) {
                                                        applicationValue = 'RNA-seq, Single Cell';
                                                    }
                                                    else if (['10x GEX', '10x VDJ'].indexOf(application) > -1){
                                                        applicationValue = 'RNA-seq, Single Cell';
                                                    }

                                                    var libraryType = null;
                                                    switch (application){
                                                        case 'RNA-seq':
                                                            libraryType = 'SMART-Seq2';
                                                            break;
                                                        case '10x VDJ':
                                                            libraryType = '10x 5\' VDJ (Rhesus A/B/G)';
                                                            break;
                                                        case '10x GEX':
                                                            libraryType = '10x 5\' GEX';
                                                    }

                                                    var name = TCRdb.panel.StimPanel.getNameFromCDNAs(r);
                                                    data.push([r.rowid, r.plateId, r.well, name, r['sortId/stimId/animalId'], r['sortId/stimId/date'], null, null, 'mRNA', 'ILLUMINA', applicationValue, chemistry, libraryType, null]);
                                                }
                                            }
                                        }, this);
                                    }, this);

                                    if (data.length === 1){
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
                                        'Library Type': 'libraryType',
                                        'Comments': 'comments',
                                        'LibraryId': 'libraryId'

                                    };

                                    var readsetToInsert = [];
                                    var cDNAsToUpdate = {};
                                    var errorMsgs = [];

                                    Ext4.Array.forEach(text, function (row, rowIdx) {
                                        var r = {};
                                        for (var headerName in headerToField){
                                            var idx = header.indexOf(headerName);
                                            if (idx !== -1 && row.length > idx){
                                                r[headerToField[headerName]] = row[idx];
                                            }
                                        }

                                        var hasBarcodes;
                                        switch (r.libraryType){
                                            case '10x 5\' GEX':
                                            case '10x 5\' VDJ (Rhesus A/B/G)':
                                                hasBarcodes = !!r.barcode5 && !r.barcode3;
                                                if (!hasBarcodes) {errorMsgs.push('10x data must have the 5\' barcode but not 3\'')};
                                                break;
                                            default:
                                                hasBarcodes = !!r.barcode5 && !!r.barcode3;
                                        }

                                        if (!r.name || !r.application || !hasBarcodes || !r.libraryId){
                                            errorMsgs.push('Every row must have name, application and proper barcodes');
                                            return;
                                        }

                                        //TODO: set container to match sort
                                        // if (row.libraryId && containerMap[row.libraryId]){
                                        //     row.container = containerMap[row.libraryId];
                                        // }
                                        //
                                        // if (!row.container){
                                        //     //TODO
                                        // }

                                        if (['10x 5\' GEX', '10x 5\' VDJ (Rhesus A/B/G)'].indexOf(r.libraryType) > -1 ) {
                                            r.barcode5 = r.barcode5.toUpperCase();
                                            if (!r.barcode5.match(/^SI-GA-/)) {
                                                if (r.barcode5.length > 3) {
                                                    errorMsgs.push('Every row must have name, application and proper barcodes');
                                                }
                                                else {
                                                    r.barcode5 = 'SI-GA-' + r.barcode5;
                                                }
                                            }
                                        }

                                        readsetToInsert.push(r);

                                        cDNAsToUpdate[r.libraryId] = cDNAsToUpdate[r.libraryId] || {};
                                        cDNAsToUpdate[r.libraryId].container = row.container;
                                        if ('rna-seq' === r.application.toLowerCase()){
                                            cDNAsToUpdate[r.libraryId].readsetIdx = rowIdx;
                                        }
                                        else if ('rna-seq, single cell' === r.application.toLowerCase() && r.libraryType === '10x 5\' GEX'){
                                            cDNAsToUpdate[r.libraryId].readsetIdx = rowIdx;
                                        }
                                        else if ('rna-seq + enrichment' === r.application.toLowerCase()){
                                            cDNAsToUpdate[r.libraryId].enrichedReadsetIdx = rowIdx;
                                        }
                                        else if ('rna-seq, single cell' === r.application.toLowerCase() && r.libraryType === '10x 5\' VDJ (Rhesus A/B/G)'){
                                            cDNAsToUpdate[r.libraryId].enrichedReadsetIdx = rowIdx;
                                        }
                                        else {
                                            errorMsgs.push('Unknown application/libraryType: ' + r.application + ' / ' + r.libraryType);
                                        }
                                    }, this);

                                    if (errorMsgs.length){
                                        errorMsgs = Ext4.unique(errorMsgs);
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

                                                if (Ext4.isDefined(cDNAsToUpdate[libraryId].container)){
                                                    r.container = cDNAsToUpdate[libraryId].container;
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
                    text: 'Shipment List',
                    linkCls: 'labkey-text-link',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', Laboratory.Utils.getQueryContainerPath(), {schemaName: 'lists', queryName: 'MedGenomeShipments'})
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
                    hidden: LABKEY.Security.currentContainer.type !== 'workbook',
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

            if (plateIds.length === 0){
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
        }
    }
});