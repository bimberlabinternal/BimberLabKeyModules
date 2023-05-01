/**
 * @cfg dataRegionName
 */
Ext4.define('MCC.window.ChangeStatusWindow', {
    extend: 'Ext.window.Window',

    fieldName: 'disabled',

    statics: {
        buttonHandler: function(dataRegionName, requestId, status){
            Ext4.create('MCC.window.ChangeStatusWindow', {
                dataRegionName: dataRegionName,
                requestId: requestId,
                status: status
            }).show();
        },
    },

    initComponent: function(){
        Ext4.apply(this, {
            modal: true,
            title: 'Change Status',
            closeAction: 'destroy',
            width: 380,
            items: [{
                bodyStyle: 'padding: 5px;',
                html: 'This will update request #' + this.requestId + ' to: ' + this.status
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
        var dataRegion = this.dataRegionName ? LABKEY.DataRegions[this.dataRegionName] : null;

        LABKEY.Query.updateRows({
            method: 'POST',
            schemaName: 'mcc',
            queryName: 'animalRequests',
            rows: [{
                rowid: this.requestId,
                status: this.status
            }],
            scope: this,
            success: function(){
                this.close();
                Ext4.Msg.hide();
                if (dataRegion) {
                    dataRegion.refresh();
                }
            },
            failure: LDK.Utils.getErrorCallback()
        });
    }
});