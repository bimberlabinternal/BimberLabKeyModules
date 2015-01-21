Ext4.define('VariantDB.window.VariantImportWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function (dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length){
                Ext4.Msg.alert('Error', 'No records selected');
                return;
            }

            //check if files can be used

            Ext4.create('VariantDB.window.VariantImportWindow', {
                outputFileIds: checked
            }).show();
        }
    },

    initComponent: function(){
        Ext4.QuickTips.init();
        Ext4.apply(this, {
            modal: true,
            title: 'Import Variants',
            width: 500,
            bodyStyle: 'padding: 5px;',
            defaults: {
                border: false,
                labelWidth: 180
            },
            items: [{
                html: 'This will iterate the provided files, merging the variants from that file into the site\'s reference list of variants.<br>',
                style: 'padding-bottom: 10px;'
            }],
            buttons: [{
                text: 'Start Import',
                scope: this,
                handler: this.onSubmit
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
        Ext4.Msg.wait('Saving...');
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('variantdb', 'variantImport'),
            jsonData: {
                outputFileIds: this.outputFileIds
            },
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function(){
                Ext4.Msg.hide();
                Ext4.Msg.alert('Success', 'Job started!', function(){
                    this.close();
                    window.location = LABKEY.ActionURL.buildURL('pipeline', 'begin');
                }, this);
            }
        });
    }
});