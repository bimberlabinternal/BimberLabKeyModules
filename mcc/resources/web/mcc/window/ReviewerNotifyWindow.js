Ext4.define('MCC.window.ReviewerNotifyWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function (dataRegionName) {
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length) {
                Ext4.Msg.alert('Error', 'No records selected');
                return;
            }

            Ext4.create('MCC.window.ReviewerNotifyWindow', {
                dataRegionName: dataRegionName,
                rowIds: checked
            }).show();
        }
    },

    initComponent: function(){
        Ext4.apply(this, {
            bodyStyle: 'padding: 5px;',
            width: 500,
            modal: true,
            title: 'Send Reviewer Notifications',
            items: [{
                html: 'This will send a notification email to the reviewers assigned to the ' + this.rowIds.length + ' selected records.',
                border: false,
                style: 'padding-bottom: 10px;'
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

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('mcc', 'notifyReviewers'),
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: LABKEY.Utils.getCallbackWrapper(function (results) {
                Ext4.Msg.hide();
                Ext4.Msg.alert('Success', 'Notification Emails Sent');
                btn.up('window').close();
            }, this),
            jsonData: {
                rowIds: this.rowIds
            }
        });
    }
});