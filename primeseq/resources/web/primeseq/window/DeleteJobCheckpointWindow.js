Ext4.define('Primeseq.window.DeleteJobCheckpointWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(dataRegionName){
            const checked = LABKEY.DataRegions[dataRegionName].getChecked();
            if (!checked.length){
                Ext4.Msg.alert('Error', 'No rows selected');
                return;
            }

            Ext4.create('Primeseq.window.DeleteJobCheckpointWindow', {
                jobIds: checked,
                dataRegionName: dataRegionName,
                autoShow: true
            });
        }
    },

    initComponent: function(){
        Ext4.QuickTips.init();
        Ext4.apply(this, {
            title: 'Update Job Resources',
            width: 600,
            bodyStyle: 'padding: 5px;',
            items: [{
                html: 'This will delete the checkpoint JSON file for the selected pipeline jobs, which will cause them to completely restart, instead of attempting to resume at the last checkpoint. This will only delete a checkpoint file for jobs with a status of error or cancelled.',
                border: false,
                style: 'padding-bottom: 20px;'
            },{
                xtype: 'checkbox',
                itemId: 'restartJobs',
                fieldLabel: 'Restart Jobs',
                checked: true
            }],
            buttons: [{
                text: 'Submit',
                handler: this.onSubmit,
                scope: this
            },{
                text: 'Cancel',
                handler: function (btn) {
                    btn.up('window').close();
                }
            }]
        });

        this.callParent(arguments);
    },

    onSubmit: function(btn){
        Ext4.Msg.wait('Loading...');
        var restartJobs = btn.up('window').down('#restartJobs');

        LABKEY.Ajax.request({
            method: 'POST',
            url: LABKEY.ActionURL.buildURL('primeseq', 'deleteJobCheckpoint'),
            jsonData: {
                jobIds: this.jobIds.join(','),
                restartJobs: !!restartJobs
            },
            scope: this,
            success: function(response){
                Ext4.Msg.hide();

                var jsonResp = LABKEY.Utils.decode(response.responseText);
                console.log(jsonResp);
                var dataRegionName = this.dataRegionName;
                this.close();

                Ext4.Msg.alert('Success', 'Jobs updated: ' + jsonResp.jobsUpdated + '<br>Jobs restarted: ' + jsonResp.jobsRestarted, function(){
                    var dataRegion = LABKEY.DataRegions[dataRegionName];
                    if (dataRegion){
                        dataRegion.refresh();
                    }
                }, this);
            },
            failure: LDK.Utils.getErrorCallback()
        });
    }
});