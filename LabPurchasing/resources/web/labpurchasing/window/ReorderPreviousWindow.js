Ext4.define('LabPurchasing.window.ReorderPreviousWindow', {
    extend: 'Ext.window.Window',
    alias: 'widget.labpurchasing-reorderpreviouswindow',

    initComponent: function () {
        Ext4.apply(this, {
            modal: true,
            border: false,
            width: 700,
            closeAction: 'destroy',
            title: 'Re-order Previous Item',
            defaults: {
                border: false
            },
            bodyStyle: 'padding: 5px;',
            items: [{
                xtype: 'labkey-combo',
                itemId: 'vendorField',
                fieldLabel: 'Vendor (optional)',
                width: 650,
                labelWidth: 150,
                forceSelection: true,
                queryMode: 'local',
                caseSensitive: false,
                anyMatch: true,
                store: {
                    type: 'labkey-store',
                    schemaName: 'labpurchasing',
                    queryName: 'vendors',
                    autoLoad: true
                },
                valueField: 'rowId',
                displayField: 'vendorName',
                listeners: {
                    scope: this,
                    change: function(field, val){
                        var store = this.down('#itemField').store;
                        if (val) {
                            store.filterArray = [LABKEY.Filter.create('vendorId', val, LABKEY.Filter.Types.EQUAL)];
                        }
                        else {
                            store.filterArray = null;
                        }

                        store.removeAll();
                        store.load();
                    }
                }
            }, {
                xtype: 'labkey-combo',
                itemId: 'itemField',
                fieldLabel: 'Item',
                allowBlank: false,
                forceSelection: true,
                queryMode: 'local',
                caseSensitive: false,
                anyMatch: true,
                width: 650,
                labelWidth: 150,
                store: {
                    type: 'labkey-store',
                    schemaName: 'labpurchasing',
                    queryName: 'referenceItems',
                    columns: 'rowId,vendorId,itemName,itemAndNumber,itemNumber,units,unitCost',
                    autoLoad: true
                },
                valueField: 'rowId',
                displayField: 'itemAndNumber',
                listeners: {
                    scope: this,
                    afterrender: function (field) {
                        Ext4.defer(field.focus, 200, field);
                    },
                    specialkey: function (field, e) {
                        if (!field.isExpanded && field.getValue() && e.getKey() === e.ENTER) {
                            this.doSubmit();
                        }
                    }
                }
            },{
                xtype: 'ldk-linkbutton',
                linkTarget: '_blank',
                text: 'View/Manage Reference Items',
                linkCls: 'labkey-text-link',
                href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'labpurchasing', queryName: 'referenceItems'}),
                style: 'padding-top: 10px;'

            }],
            buttons: [{
                text: 'Re-order Item',
                scope: this,
                handler: this.doSubmit
            },{
                text: 'Cancel',
                handler: function(btn) {
                    btn.up('window').close();
                }
            }]
        });

        this.callParent(arguments);
    },

    doSubmit: function(){
        var field = this.down('#itemField');
        var recIdx = field.store.find('rowId', field.getValue());
        if (recIdx === -1) {
            Ext4.Msg.alert('Error', 'Must select an item');
            return;
        }

        var rec = field.store.getAt(recIdx);
        var data = {
            requestor: LABKEY.Security.currentUser.id,
            itemId: rec.get('rowId'),
            vendorId: rec.get('vendorId'),
            itemName: rec.get('itemName'),
            itemNumber: rec.get('itemNumber'),
            units: rec.get('units'),
            unitCost: rec.get('unitCost')
        };

        if (this.formPanel) {
            this.formPanel.getForm().setValues(data);
        }
        else {
            this.gridPanel.store.insert(0, this.gridPanel.store.createModel(data));

            var cellEditing = this.gridPanel.getPlugin(this.gridPanel.editingPluginId);
            if (cellEditing) {
                cellEditing.completeEdit();
                cellEditing.startEditByPosition({row: 0, column: 5});
            }
        }

        this.close();
    }
});
