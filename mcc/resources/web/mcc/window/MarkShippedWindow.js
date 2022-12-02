Ext4.define('MCC.window.MarkShippedWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function (dataRegionName) {
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length) {
                Ext4.Msg.alert('Error', 'No records selected');
                return;
            }

            if (checked.length !== 1) {
                Ext4.Msg.alert('Error', 'Currently only one ID is supported as a time');
                return;
            }

            Ext4.create('MCC.window.MarkShippedWindow', {
                dataRegionName: dataRegionName,
                rowIds: checked
            }).show();
        }
    },

    initComponent: function(){
        var ctx = MCC.Utils.getMCCContext();
        Ext4.apply(this, {
            bodyStyle: 'padding: 5px;',
            width: 500,
            modal: true,
            title: 'Mark ID Shipped',
            defaults: {
                labelWidth: 200,
                width: 450,
            },
            items: [{
                html: 'This will: <br>1) Mark the selected animals as shipped from this center<br>2) Enter a new demographics record in the selected study<br>3) Preserve the MCC ID for each animal.',
                border: false,
                style: 'padding-bottom: 10px;'
            },{
                xtype: 'textfield',
                fieldLabel: 'New ID (blank if unchanged)',
                itemId: 'newId'
            },{
                xtype: 'datefield',
                fieldLabel: 'Effective Date',
                itemId: 'effectiveDate',
                allowBlank: false
            },{
                xtype: 'labkey-combo',
                fieldLabel: 'Destination Center Name',
                itemId: 'centerName',
                displayField: 'colony',
                valueField: 'colony',
                triggerAction: 'all',
                queryMode: 'local',
                forceSelection: true,
                allowBlank: false,
                plugins: ['ldk-usereditablecombo'],
                store: {
                    type: 'labkey-store',
                    containerPath: ctx.MCCContainer,
                    schemaName: 'study',
                    sql: 'SELECT DISTINCT colony FROM study.demographics',
                    sort: 'colony',
                    autoLoad: true
                }
            },{
                xtype: 'labkey-combo',
                fieldLabel: 'Target Folder',
                itemId: 'targetFolder',
                allowBlank: false,
                displayField: 'Name',
                valueField: 'Path',
                triggerAction: 'all',
                queryMode: 'local',
                forceSelection: true,
                store: {
                    type: 'labkey-store',
                    containerPath: ctx.MCCInternalDataContainer,
                    schemaName: 'core',
                    queryName: 'containers',
                    columns: 'EntityId,Name,Parent,Path',
                    containerFilter: 'CurrentAndSubfolders',
                    sort: 'Name',
                    autoLoad: true,
                    listeners: {
                        load: function(store) {
                            store.filterBy(function(r) {
                                return(r.get('Path') !== ctx.MCCInternalDataContainer);
                            })
                        }
                    }
                }
            }],
            buttons: [{
                text: 'Submit',
                handler: this.onSubmit,
                scope: this
            },{
                text: 'Cancel',
                handler: function(btn){
                    btn.up('window').close();
                }
            }]
        });

        this.callParent(arguments);
    },

    onSubmit: function(btn){
        Ext4.Msg.wait('Loading...');

        var win = btn.up('window');
        var lsid = win.rowIds[0];
        var effectiveDate = win.down('#effectiveDate').getValue();
        var centerName = win.down('#centerName').getValue();
        var targetFolder = win.down('#targetFolder').getValue();
        if (!effectiveDate || !centerName || !targetFolder) {
            Ext4.Msg.alert('Error', 'Must provide date, center name, and target folder');
            return;
        }

        Ext4.Msg.wait('Saving...');
        LABKEY.Query.selectRows({
            schemaName: 'study',
            queryName: 'Demographics',
            filterArray: [LABKEY.Filter.create('lsid', lsid)],
            columns: 'Id,gender,colony,species,birth,death,center,Id/MostRecentDeparture/MostRecentDeparture,Id/mccAlias/externalAlias,calculated_status',
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(results) {
                if (!results || !results.rows || !results.rows.length) {
                    Ext4.Msg.hide();
                    Ext4.Msg.alert('Error', 'No row round for LSID: ' + lsid + '. This is not expected');
                    return;
                }

                var row = results.rows[0];
                var newId = win.down('#newId').getValue() || row.Id;

                var commands = [];
                if (!row['Id/MostRecentDeparture/MostRecentDeparture']) {
                    //TODO: make this update demographics.colony
                    commands.push({
                        command: 'insert',
                        schemaName: 'study',
                        queryName: 'Departure',
                        rows: [{
                            Id: row.Id,
                            date: effectiveDate,
                            destination: centerName,
                            qcstate: null,
                            objectId: null,
                            QCStateLabel: 'Completed'
                        }]
                    });
                }

                commands.push({
                    command: 'insert',
                    containerPath: targetFolder,
                    schemaName: 'study',
                    queryName: 'Demographics',
                    rows: [{
                        Id: newId,
                        date: effectiveDate,
                        alternateIds: row.Id !== newId ? row.Id : null,
                        gender: row.gender,
                        species: row.species,
                        birth: row.birth,
                        death: row.death,
                        colony: centerName,
                        source: row.colony,
                        calculated_status: 'Alive',
                        skipMccAliasCreation: true,
                        QCState: null,
                        QCStateLabel: 'Completed',
                        objectId: null
                    }]
                });

                commands.push({
                    command: 'insert',
                    containerPath: targetFolder,
                    schemaName: 'mcc',
                    queryName: 'animalMapping',
                    rows: [{
                        subjectname: newId,
                        externalAlias: row['Id/mccAlias/externalAlias']
                    }]
                });

                commands.push({
                    command: 'update',
                    containerPath: null, //Use current folder
                    schemaName: 'study',
                    queryName: 'Demographics',
                    rows: [{
                        Id: newId,
                        excludeFromCensus: true
                    }]
                });

                LABKEY.Query.saveRows({
                    commands: commands,
                    scope: this,
                    failure: LDK.Utils.getErrorCallback(),
                    success: function() {
                        Ext4.Msg.hide();
                        Ext4.Msg.alert('Success', 'Transfer Added', function () {
                            var dataRegion = LABKEY.DataRegions[this.dataRegionName];
                            this.destroy();

                            dataRegion.refresh();
                        }, this);
                    }
                });
            }
        });
    }
});