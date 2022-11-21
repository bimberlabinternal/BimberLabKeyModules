/**
 * @cfg dataRegionName
 */
Ext4.define('LabPurchasing.window.EnterOrderInfoWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            if (!dataRegion || !dataRegion.getChecked() || !dataRegion.getChecked().length){
                Ext4.Msg.alert('Error', 'No rows selected');
                return;
            }

            Ext4.create('LabPurchasing.window.EnterOrderInfoWindow', {
                dataRegionName: dataRegionName
            }).show();
        }
    },

    initComponent: function(){
        Ext4.apply(this, {
            modal: true,
            title: 'Order Items',
            closeAction: 'destroy',
            width: 380,
            items: [{
                bodyStyle: 'padding: 5px;',
                items: [{
                    xtype: 'datefield',
                    fieldLabel: 'Date Ordered',
                    width: 350,
                    itemId: 'orderDate',
                    value: new Date()
                },{
                    xtype: 'textfield',
                    fieldLabel: 'Ordered By',
                    width: 350,
                    itemId: 'orderedBy',
                    value: LABKEY.Security.currentUser.displayName
                },{
                    xtype: 'textfield',
                    fieldLabel: 'Order Number',
                    width: 350,
                    itemId: 'orderNumber'
                }]
            }],
            buttons: [{
                text:'Submit',
                disabled:false,
                scope: this,
                handler: this.onSubmit
            },{
                text: 'Close',
                handler: function(btn){
                    btn.up('window').close();
                }
            }]
        });

        this.callParent(arguments);
    },

    onSubmit: function(btn){
        Ext4.Msg.wait('Loading...');
        var orderDate = btn.up('window').down('#orderDate').getValue();
        var orderedBy = btn.up('window').down('#orderedBy').getValue();
        var orderNumber = btn.up('window').down('#orderNumber').getValue();

        if (!orderDate || !orderedBy){
            Ext4.Msg.alert('Error', 'Must enter the order date and order by fields');
            return;
        }

        var dataRegion = LABKEY.DataRegions[this.dataRegionName];
        var checked = dataRegion.getChecked();

        var toUpdate = [];
        Ext4.Array.forEach(checked, function(x){
            toUpdate.push({
                rowId: x,
                orderDate: orderDate,
                orderedBy: orderedBy,
                orderNumber: orderNumber
            });
        }, this);

        Ext4.Msg.wait('Saving...');
        LABKEY.Query.updateRows({
            method: 'POST',
            schemaName: 'labpurchasing',
            queryName: 'purchases',
            rows: toUpdate,
            scope: this,
            success: function(){
                this.close();
                Ext4.Msg.hide();
                dataRegion.selectNone();
                dataRegion.refresh();
            },
            failure: LDK.Utils.getErrorCallback()
        });
    }
});