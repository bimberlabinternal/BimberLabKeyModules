/**
 * @cfg dataRegionName
 */
Ext4.define('LabPurchasing.window.MarkReceivedWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            if (!dataRegion || !dataRegion.getChecked() || !dataRegion.getChecked().length){
                Ext4.Msg.alert('Error', 'No rows selected');
                return;
            }

            Ext4.create('LabPurchasing.window.MarkReceivedWindow', {
                dataRegionName: dataRegionName
            }).show();
        }
    },

    initComponent: function(){
        Ext4.apply(this, {
            modal: true,
            title: 'Mark Received',
            closeAction: 'destroy',
            width: 380,
            items: [{
                bodyStyle: 'padding: 5px;',
                items: [{
                    xtype: 'datefield',
                    fieldLabel: 'Date Received',
                    width: 350,
                    itemId: 'dateReceived',
                    value: new Date()
                },{
                    xtype: 'textfield',
                    fieldLabel: 'Item Location',
                    width: 350,
                    itemId: 'itemLocation'
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
        var dateReceived = btn.up('window').down('#dateReceived').getValue();
        if(!dateReceived){
            Ext4.Msg.alert('Error', 'Must enter the date received');
            return;
        }

        var itemLocation = btn.up('window').down('#itemLocation').getValue();

        var dataRegion = LABKEY.DataRegions[this.dataRegionName];
        var checked = dataRegion.getChecked();

        var toUpdate = [];
        Ext4.Array.forEach(checked, function(x){
            toUpdate.push({
                rowId: x,
                receivedDate: dateReceived,
                itemLocation: itemLocation
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