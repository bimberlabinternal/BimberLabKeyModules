Ext4.define('LabPurchasing.window.ReorderPreviousWindow', {
    extend: 'Ext.window.Window',
    alias: 'widget.labpurchasing-reorderpreviouswindow',

    initComponent: function () {
        Ext4.apply(this, {
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
                        console.log('change: ' + store.getCount());
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
            },{
                xtype: 'labkey-combo',
                itemId: 'itemField',
                fieldLabel: 'Item',
                allowBlank: false,
                forceSelection: true,
                queryMode: 'local',
                width: 650,
                labelWidth: 150,
                store: {
                    type: 'labkey-store',
                    schemaName: 'labpurchasing',
                    queryName: 'referenceItems',
                    columns: 'rowId,vendorId,itemName,itemNumber,units,unitCost',
                    autoLoad: true,
                    listeners: {
                        load: function(store) {
                            console.log('load: ' + store.getCount());
                        }
                    }
                },
                valueField: 'rowId',
                displayField: 'itemName'
            }],
            buttons: [{
                text: 'Re-order Item',
                scope: this,
                handler: function (btn) {
                    var field = this.down('#itemField');
                    var recIdx = field.store.find('rowId', field.getValue());
                    if (recIdx === -1) {
                        Ext4.Msg.alert('Error', 'Must select an item');
                        return;
                    }

                    var rec = field.store.getAt(recIdx);
                    this.formPanel.getForm().setValues({
                        itemId: rec.get('rowId'),
                        vendorId: rec.get('vendorId'),
                        itemName: rec.get('itemName'),
                        itemNumber: rec.get('itemNumber'),
                        units: rec.get('units'),
                        unitCost: rec.get('unitCost')
                    });

                    btn.up('window').close();
                }
            },{
                text: 'Cancel',
                handler: function(btn) {
                    btn.up('window').close();
                }
            }]
        });

        this.callParent(arguments);
    },
});
