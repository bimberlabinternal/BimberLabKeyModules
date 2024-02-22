Ext4.define('MCC.window.RenameIdWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function (dataRegionName) {
            Ext4.create('MCC.window.RenameIdWindow', {
                dataRegionName: dataRegionName
            }).show();
        }
    },

    initComponent: function() {
        Ext4.apply(this, {
            title: 'Rename ID(s)',
            bodyStyle: 'padding: 5px;',
            defaults: {
                bodyStyle: 'padding: 5px;'
            },
            maxWidth: 550,
            items: [{
                html: 'This will globally change all records associated with one animal ID to reference a new ID. This should not be used often, and is primarily intended for the situation where an incorrect ID was entered, or the source center provided a different ID after the fact. If an animal was shipped, it is generally fine to allow existing records to reference the original colony\'s ID, since both old and new IDs should point to the same MCC ID.',
                style: 'padding-bottom: 10px;',
                width: 500,
                border: false
            }, {
                xtype: 'textarea',
                width: 500,
                height: 250,
                boxLabel: 'Enter Old/New IDs, one pair per line:'
            }],
            buttons: [{
                text: 'Submit',
                scope: this,
                handler: this.doSubmit
            }, {
                text: 'Cancel',
                handler: function (btn) {
                    btn.up('window').close();
                }
            }]
        });

        this.callParent(arguments);
    },

    doSubmit: function(btn){
        var text = btn.up('window').down('textarea').getValue();
        if (!text) {
            Ext4.Msg.alert('Error', 'No rows entered!');
            return;
        }

        var rows = text.split(/[\n\r]/)
        if (!rows.length) {
            Ext4.Msg.alert('Error', 'No rows entered!');
            return;
        }

        var oldToNew = {};
        var hadError = false;
        Ext4.Array.forEach(rows, function(r){
            r = r.split(/[\t ;,]/)
            if (r.length !== 2) {
                Ext4.Msg.alert('Error', 'Row was not two elements long: ' + r.join(','));
                hadError = true;
                return false;
            }

            oldToNew[r[0]] = r[1];
        }, this);

        if (hadError) {
            return;
        }

        Ext4.Msg.wait('Saving...');
        LABKEY.Ajax.request({
            method: 'POST',
            url: LABKEY.ActionURL.buildURL('mcc', 'renameIds'),
            scope: this,
            params: {
                originalIds: Ext4.Object.getKeys(oldToNew),
                newIds: Ext4.Object.getValues(oldToNew)
            },
            success: LABKEY.Utils.getCallbackWrapper(function (response) {
                Ext4.Msg.hide();
                this.close();
                console.log(response);

                Ext4.Msg.alert('Success', 'Total IDs Updated: ' + response.totalIdsUpdated + '<br>Total Records Updated: ' + response.totalRecordsUpdated);
            }),
            failure: LDK.Utils.getErrorCallback()
        });
    }
});
