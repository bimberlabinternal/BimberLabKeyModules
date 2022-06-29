Ext4.define('Primeseq.window.UpdateJobResourcesWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(dataRegionName){
            const checked = LABKEY.DataRegions[dataRegionName].getChecked();
            if (!checked.length){
                Ext4.Msg.alert('Error', 'No rows selected');
                return;
            }

            Ext4.create('Primeseq.window.UpdateJobResourcesWindow', {
                jobIds: checked,
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
                html: 'This can be used to update the resources requested from the cluster. This will not change in-flight jobs. The job must be stopped and restarted for any changes to take effect.',
                border: false,
                style: 'padding-bottom: 20px;'
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

        Ext4.Msg.wait('Loading...');
        LABKEY.Ajax.request({
            method: 'POST',
            url: LABKEY.ActionURL.buildURL('primeseq', 'getResourceSettingsForJob', null),
            scope: this,
            jsonData: {
                jobIds: this.jobIds.join(',')
            },
            success: function(response){
                LDK.Utils.decodeHttpResponseJson(response);
                if (response.responseJSON){
                    var cfg = this.getJobResourcesCfg(response.responseJSON);
                    LDK.Assert.assertNotEmpty('Error loading cluster configuration for job(s): ' + this.jobIds.join(','), cfg);

                    if (cfg) {
                        this.add(cfg);
                    }
                    else {
                        this.add('There was an error loading the cluster configuration');
                    }
                }

                Ext4.Msg.hide();
            },
            failure: LDK.Utils.getErrorCallback()
        });
    },

    getJobResourcesCfg: function(results){
        return {
            xtype: 'sequenceanalysis-analysissectionpanel',
            itemId: 'analysissectionpanel',
            border: false,
            stepType: 'resourceSettings',
            sectionDescription: '',
            toolConfig: {
                resourceSettings: [results]
            },
            toolIdx: 0
        }
    },

    onSubmit: function(){
        Ext4.Msg.wait('Loading...');

        var panel = this.down('#analysissectionpanel');
        LDK.Assert.assertNotEmpty('analysissectionpanel was null in UpdateJobResourcesWindow', panel);
        const json = panel.toJSON();
        LABKEY.Ajax.request({
            method: 'POST',
            url: LABKEY.ActionURL.buildURL('primeseq', 'setResourceSettingsForJob'),
            jsonData: {
                jobIds: this.jobIds.join(','),
                paramJson: JSON.stringify(json)
            },
            scope: this,
            success: function(response){
                Ext4.Msg.hide();
                this.close();

                Ext4.Msg.alert('Success', 'Job settings have been updated');
            },
            failure: LDK.Utils.getErrorCallback()
        });
    }
});