Ext4.define('LabPurchasing.panel.OrderItemsGrid', {
    extend: 'LDK.grid.Panel',
    alias: 'widget.labpurchasing-orderitemsgrid',

    showPlaceOrderUI: false,

    initComponent: function() {
        Ext4.apply(this, {
            border: true,
            minHeight: 300,
            width: '100%',
            xtype: 'ldk-gridpanel',
            clicksToEdit: 1,
            // This causes the editor plugin to begin on the vendor column
            firstEditableColumn: 1,
            listeners: {
                scope: this,
                reconfigure: function(){
                    // NOTE: this was added strictly to automated tests. Apparently this.columns remains the config objects, while ColumnManager.columns are the column instances.
                    // Ext4GridRef relies on the latter, and this is a fix to ensure getIndexOfColumn() works
                    this.columns = this.columnManager.columns;
                }
            },
            store: {
                type: 'labkey-store',
                schemaName: 'labpurchasing',
                queryName: 'purchases',
                filterArray: this.rowIds && this.rowIds.length ? [LABKEY.Filter.create('rowId', this.rowIds.join(';'), LABKEY.Filter.Types.IN)] : [],
                maxRows: this.rowIds && this.rowIds.length ? -1 : 0,
                columns: 'requestor,vendorId,itemName,itemNumber,units,quantity,unitCost,totalCost,description,fundingSource,orderedBy,orderDate,orderNumber,emailOnArrival',
                autoLoad: true,
                listeners: {
                    scope: this,
                    load: this.showPlaceOrderUI ? function(store) {
                        store.each(function(r){
                            if (!r.get('orderDate')) {
                                r.set('orderDate', new Date());
                            }

                            if (!r.get('orderedBy')) {
                                r.set('orderedBy', LABKEY.Security.currentUser.displayName);
                            }
                        }, this);
                    } : Ext4.emptyFn,
                    update: function(store, rec) {
                        if (rec.get('unitCost') && rec.get('quantity')) {
                            rec.set('totalCost', rec.get('unitCost') * rec.get('quantity'));
                        }
                    }
                },
                metadata: {
                    rowId: {
                        hidden: true,
                        nullable: true
                    },
                    requestor: {
                        fixedWidthCol: true,
                        defaultValue: LABKEY.Security.currentUser.id,
                        editorConfig: {
                            initialValue: LABKEY.Security.currentUser.id
                        },
                        columnConfig: {
                            width: 100,
                            header: 'Requestor',
                            showLink: false
                        },
                        allowBlank: false
                    },
                    vendorId: {
                        fixedWidthCol: true,
                        required: true,
                        columnConfig: {
                            width: 250,
                            showLink: false
                        }
                    },
                    itemName: {
                        fixedWidthCol: true,
                        columnConfig: {
                            width: 175
                        }
                    },
                    itemNumber: {
                        fixedWidthCol: true,
                        columnConfig: {
                            width: 150
                        }
                    },
                    units: {
                        fixedWidthCol: true,
                        editorConfig: {
                            plugins: ['ldk-usereditablecombo']
                        },
                        columnConfig: {
                            width: 100
                        }
                    },
                    quantity: {
                        fixedWidthCol: true,
                        editorConfig: {
                            xtype: 'ldk-numberfield',
                            minValue: 0
                        },
                        columnConfig: {
                            width: 100
                        }
                    },
                    unitCost: {
                        fixedWidthCol: true,
                        editorConfig: {
                            xtype: 'ldk-numberfield',
                            minValue: 0
                        },
                        columnConfig: {
                            width: 100
                        }
                    },
                    totalCost: {
                        fixedWidthCol: true,
                        editorConfig: {
                            xtype: 'ldk-numberfield',
                            minValue: 0
                        },
                        columnConfig: {
                            width: 100
                        }
                    },
                    description: {
                        fixedWidthCol: true,
                        columnConfig: {
                            width: 200
                        },
                        editorConfig: {
                            xtype: 'textarea',
                            height: 200
                        }
                    },
                    fundingSource: {
                        fixedWidthCol: true,
                        columnConfig: {
                            width: 150
                        }
                    },
                    orderedBy: {
                        fixedWidthCol: true,
                        defaultValue: this.showPlaceOrderUI ? LABKEY.Security.currentUser.displayName : null,
                        columnConfig: {
                            hidden: !this.showPlaceOrderUI,
                            width: 125
                        }
                    },
                    orderDate: {
                        fixedWidthCol: true,
                        defaultValue: this.showPlaceOrderUI ? new Date() : null,
                        columnConfig: {
                            hidden: !this.showPlaceOrderUI,
                            width: 100
                        }
                    },
                    orderNumber: {
                        fixedWidthCol: true,
                        columnConfig: {
                            hidden: !this.showPlaceOrderUI,
                            width: 120
                        }
                    },
                    emailOnArrival: {
                        defaultValue: true,
                        columnConfig: {
                            renderer: function(val) {
                                return(val ? 'Yes' : 'No');
                            }
                        }
                    }
                }
            },
            buttons: [{
                text: this.showPlaceOrderUI ? 'Save Changes' : 'Order Items',
                scope: this,
                handler: function (btn) {
                    Ext4.Msg.wait('Saving...');
                    var store = btn.up('grid').store;

                    var missing = [];
                    store.each(function (r) {
                        Ext4.Array.forEach(r.store.model.getFields(), function (f) {
                            if (f.nullable === false && !r.get(f.dataIndex)) {
                                missing.push(f.caption || f.name);
                            }
                        }, this);
                    }, this);

                    if (missing.length) {
                        Ext4.Msg.hide();
                        Ext4.Msg.alert('Error', 'Missing one or more fields: ' + Ext4.unique(missing).join(', '));
                        return;
                    }

                    if (!store.syncNeeded()) {
                        Ext4.Msg.hide();
                        Ext4.Msg.alert('No changes', 'There are no changes to save', function () {
                            window.location = LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {
                                schemaName: 'labpurchasing',
                                queryName: 'purchases'
                            })
                        }, this);
                    }

                    store.sync({
                        scope: this,
                        failure: LDK.Utils.getErrorCallback(),
                        success: function () {
                            Ext4.Msg.hide();
                            Ext4.Msg.alert('Success', (this.showPlaceOrderUI ? 'Items Updated' : 'Items Ordered'), function () {
                                window.location = LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {
                                    schemaName: 'labpurchasing',
                                    queryName: 'purchases'
                                });
                            }, this);
                        }
                    });
                }
            },{
                text: 'Cancel',
                handler: function (btn) {
                    window.location = LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {
                        schemaName: 'labpurchasing',
                        queryName: 'purchases'
                    });
                }
            }],
            buttonAlign: 'left',
            tbar: [LABKEY.ext4.GRIDBUTTONS.ADDRECORD({
                text: 'Add New',
                hidden: this.showPlaceOrderUI,
            }), {
                text: 'Remove Selected',
                scope: this,
                handler: function (btn) {
                    var grid = btn.up('gridpanel');
                    var selections = grid.getSelectionModel().getSelection();

                    if (!grid.store || !selections || !selections.length)
                        return;

                    grid.store.remove(selections);
                    grid.store.removed = []; //prevent these from being deleted
                }
            }, {
                text: 'Add New Vendor',
                scope: this,
                handler: function (btn) {
                    Ext4.create('Ext4.window.Window', {
                        modal: true,
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
            },{
                text: 'Re-order Previous Item',
                scope: this,
                handler: function (btn) {
                    Ext4.create('LabPurchasing.window.ReorderPreviousWindow', {
                        gridPanel: this
                    }).show();

                }
            }]
        });

        this.callParent(arguments);
    }
});
