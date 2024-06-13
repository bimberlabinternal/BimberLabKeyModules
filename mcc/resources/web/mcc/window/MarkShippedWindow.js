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
            }, this.getAnimalIdFields()],
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

    getAnimalIdFields: function(){
        var fields = [{
            xtype: 'displayfield',
            value: 'Animal ID',
            width: 150
        },{
            xtype: 'displayfield',
            value: 'Keep Existing ID?',
            width: 150
        },{
            xtype: 'displayfield',
            value: 'New ID (blank if unchanged)',
        }];

        Ext4.Array.forEach(this.rowIds, function(rowId){
            const animalId = this.lsidToAnimalId(rowId);
            fields = fields.concat([{
                xtype: 'displayfield',
                value: animalId,
            },{
                xtype: 'checkbox',
                itemId: 'usePreviousId-' + animalId,
                checked: false,
                listeners: {
                    scope: this,
                    change: function (field, val) {
                        var target = field.up('panel').down('#newId-' + animalId);
                        target.allowBlank = !!val;
                        target.setDisabled(val);
                    }
                }
            },{
                xtype: 'textfield',
                itemId: 'newId-' + animalId,
                disabled: false,
                allowBlank: true
            }]);
        }, this);

        return {
            layout: {
                type: 'table',
                columns: 3
            },
            border: false,
            defaults: {
                style: 'padding:5px;',
                border: false
            },
            items: fields
        };
    },

    lsidToAnimalId: function(lsid){
        lsid = lsid.split(':')[4];
        lsid = lsid.split('.');
        lsid.shift();

        return lsid.join('.');
    },

    onSubmit: function(btn){
        Ext4.Msg.wait('Loading...');

        var win = btn.up('window');
        var lsids = win.rowIds;
        var effectiveDate = win.down('#effectiveDate').getValue();
        var centerName = win.down('#centerName').getValue();
        var targetFolder = win.down('#targetFolder').getValue();
        if (!effectiveDate || !centerName || !targetFolder) {
            Ext4.Msg.alert('Error', 'Must provide date, center name, and target folder');
            return;
        }

        var hasError = false;
        Ext4.Array.forEach(this.rowIds, function(rowId) {
            var animalId = this.lsidToAnimalId(rowId);
            var useExisting = win.down('#usePreviousId-' + animalId).getValue();
            if (!useExisting && !win.down('#newId-' + animalId).getValue()) {
                Ext4.Msg.hide();
                Ext4.Msg.alert('Error', 'Must enter the new ID for: ' + animalId);
                hasError = true;
                return false;
            }
        }, this);

        if (hasError) {
            return;
        }

        var targetFolderId = win.down('#targetFolder').store.findRecord('Path', targetFolder).get('EntityId');
        Ext4.Msg.wait('Saving...');
        LABKEY.Query.selectRows({
            schemaName: 'study',
            queryName: 'Demographics',
            filterArray: [LABKEY.Filter.create('lsid', lsids.join(';'), LABKEY.Filter.Types.IN)],
            columns: 'Id,gender,colony,species,birth,death,center,Id/MostRecentDeparture/MostRecentDeparture,Id/mccAlias/externalAlias,calculated_status,dam,sire,damMccAlias/externalAlias,sireMccAlias/externalAlias',
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(results) {
                if (!results || !results.rows || !results.rows.length) {
                    Ext4.Msg.hide();
                    Ext4.Msg.alert('Error', 'No rows found for, this is not expected');
                    return false;
                }

                var commands = [];
                Ext4.Array.forEach(results.rows, function(row){
                    var effectiveId = win.down('#usePreviousId-' + row.Id).getValue() ? row.Id : win.down('#newId-' + row.Id).getValue();
                    // This should be checked above, although perhaps case sensitivity could get involved:
                    LDK.Assert.assertNotEmpty('Missing effective ID after query', effectiveId);

                    var shouldAddDeparture = !row['Id/MostRecentDeparture/MostRecentDeparture'] || row['Id/MostRecentDeparture/MostRecentDeparture'] !== Ext4.Date.format(row.effectiveDate, 'Y-m-d') || row.Id !== effectiveId;
                    if (shouldAddDeparture) {
                        commands.push({
                            command: 'insert',
                            schemaName: 'study',
                            queryName: 'Departure',
                            rows: [{
                                Id: row.Id,
                                date: effectiveDate,
                                destination: centerName,
                                description: row.colony ? 'Original center: ' + row.colony : null,
                                qcstate: null,
                                objectId: null,
                                QCStateLabel: 'Completed'
                            }]
                        });
                    }

                    // If going to a new LK folder, we're creating a whole new record:
                    if (targetFolderId.toUpperCase() !== LABKEY.Security.currentContainer.id.toUpperCase() || effectiveId !== row.Id) {
                        commands.push({
                            command: 'insert',
                            containerPath: targetFolder,
                            schemaName: 'study',
                            queryName: 'Demographics',
                            rows: [{
                                Id: effectiveId,
                                date: effectiveDate,
                                alternateIds: row.Id !== effectiveId ? row.Id : null,
                                gender: row.gender,
                                species: row.species,
                                birth: row.birth,
                                death: row.death,
                                dam: row.dam,
                                sire: row.sire,
                                damMccAlias: row['damMccAlias/externalAlias'],
                                sireMccAlias: row['sireMccAlias/externalAlias'],
                                colony: centerName,
                                source: row.colony,
                                calculated_status: 'Alive',
                                mccAlias: row['Id/mccAlias/externalAlias'],
                                QCState: null,
                                QCStateLabel: 'Completed',
                                objectId: null
                            }]
                        });

                        commands.push({
                            command: 'update',
                            containerPath: null, //Use current folder
                            schemaName: 'study',
                            queryName: 'Demographics',
                            rows: [{
                                Id: row.Id, // NOTE: always change the original record
                                excludeFromCensus: true
                            }]
                        });
                    }
                    else {
                        // Otherwise update the existing:
                        commands.push({
                            command: 'update',
                            containerPath: targetFolder,
                            schemaName: 'study',
                            queryName: 'Demographics',
                            rows: [{
                                Id: row.Id,
                                date: effectiveDate,
                                alternateIds: null,
                                gender: row.gender,
                                species: row.species,
                                birth: row.birth,
                                death: row.death,
                                dam: row.dam,
                                sire: row.sire,
                                colony: centerName,
                                source: row.colony,
                                calculated_status: 'Alive',
                                QCState: null,
                                QCStateLabel: 'Completed',
                                objectId: null
                            }]
                        });

                        // And also add an arrival record. NOTE: set the date after the departure to get status to update properly
                        var arrivalDate = new Date(effectiveDate).setMinutes(effectiveDate.getMinutes() + 1);
                        commands.push({
                            command: 'insert',
                            containerPath: targetFolder,
                            schemaName: 'study',
                            queryName: 'Arrival',
                            rows: [{
                                Id: effectiveId,
                                date: arrivalDate,
                                source: centerName,
                                QCState: null,
                                QCStateLabel: 'Completed',
                                objectId: null
                            }]
                        });
                    }
                }, this);

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