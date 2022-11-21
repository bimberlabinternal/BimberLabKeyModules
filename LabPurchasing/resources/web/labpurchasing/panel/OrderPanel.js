Ext4.define('LabPurchasing.panel.OrderPanel', {
    extend: 'LDK.form.Panel',
    alias: 'widget.labpurchasing-orderpanel',
    initComponent: function() {
        Ext4.apply(this, {
            border: false,
            metadata: {
                unitCost: {
                    listeners: {
                        change: function (field, val) {
                            var unitCost = val;
                            var quantity = field.up('panel').down('field[dataIndex="quantity"]').getValue();
                            if (quantity && unitCost) {
                                field.up('panel').down('field[dataIndex="totalCost"]').setValue(quantity * unitCost);
                            }
                        }
                    }
                },
                quantity: {
                    listeners: {
                        change: function (field, val) {
                            var quantity = val;
                            var unitCost = field.up('panel').down('field[dataIndex="unitCost"]').getValue();
                            if (quantity && unitCost) {
                                field.up('panel').down('field[dataIndex="totalCost"]').setValue(quantity * unitCost);
                            }
                        }
                    }
                }
            },
            store: {
                schemaName: 'labpurchasing',
                queryName: 'purchases',
                columns: 'requestor,vendorId,itemName,itemNumber,units,quantity,unitCost,totalCost,description,fundingSource',
                autoLoad: true,
                maxRows: 0,
                metadata: {
                    requestor: {
                        defaultValue: LABKEY.Security.currentUser.displayName
                    }
                }
            },
            dockedItems: [{
                xtype: 'toolbar',
                dock: 'bottom',
                ui: 'footer',
                style: 'background-color: transparent;',
                items: [
                    LABKEY.ext4.FORMBUTTONS.getButton('SUBMIT'),
                    LABKEY.ext4.FORMBUTTONS.getButton('CANCEL'),
                    {
                        text: 'Add New Vendor',
                        scope: this,
                        handler: function (btn) {
                            Ext4.create('Ext4.window.Window', {
                                title: 'Add New Vendor',
                                items: [{
                                    xtype: 'labpurchasing-vendorpanel',
                                    formPanel: this
                                }],
                                buttons: [{
                                    text: 'Add Vendor',
                                    handler: function (btn) {
                                        var panel = btn.up('window').down('form');
                                        panel.doSubmit(btn);
                                    }
                                }, {
                                    text: 'Cancel',
                                    handler: function (btn) {
                                        btn.up('window').close();
                                    }
                                }]
                            }).show();
                        }
                    }
                ]
            }, {
                xtype: 'toolbar',
                dock: 'top',
                ui: 'footer',
                style: 'background-color: transparent;',
                items: [{
                    text: 'Re-order Previous Item',
                    scope: this,
                    handler: function (btn) {
                        Ext4.create('LabPurchasing.window.ReorderPreviousWindow', {
                            formPanel: this
                        }).show();

                    }
                }]
            }]
        });

        this.callParent(arguments);
    }
});
