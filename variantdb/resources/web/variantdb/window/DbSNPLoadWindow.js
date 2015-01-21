Ext4.define('VariantDB.window.DbSNPLoadWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function (){
            Ext4.create('VariantDB.window.DbSNPLoadWindow', {

            }).show();
        }
    },

    initComponent: function(){
        Ext4.QuickTips.init();
        Ext4.apply(this, {
            modal: true,
            title: 'Import Data From dbSNP',
            width: 500,
            bodyStyle: 'padding: 5px;',
            defaults: {
                border: false,
                labelWidth: 180
            },
            items: [{
                html: 'This will load reference SNP data and clinVar functional annotations from dbSNP.  It pulls data from NCBI\'s FTP site, which is <a style="font-weight: bold;" href="ftp://ftp.ncbi.nlm.nih.gov/snp/00readme.txt" target="_blank">described here</a><br><br>' +
                        'You need to supply the name of the directory to load.  To view available directories, <a style="font-weight: bold;" href="ftp://ftp.ncbi.nlm.nih.gov/snp/organisms/" target="_blank">click here</a>.<br><br>' +
                        'An example name will likely look something like \'human_9606_b142_GRCh38\'.  Within the chosen directory, we expect to find many subfolders, including one with the name chr_rpts.',
                style: 'padding-bottom: 10px;'
            },{
                xtype: 'textfield',
                fieldLabel: 'NCBI Subfolder',
                itemId: 'snpPath',
                allowBlank: false,
                width: 400
            },{
                xtype: 'labkey-combo',
                fieldLabel: 'Genome',
                allowBlank: false,
                width: 400,
                displayField: 'name',
                valueField: 'rowid',
                store: {
                    type: 'labkey-store',
                    autoLoad: true,
                    containerPath: Laboratory.Utils.getQueryContainerPath(),
                    schemaName: 'sequenceanalysis',
                    queryName: 'reference_libraries',
                    columns: 'rowid,name'
                },
                itemId: 'genomeId'
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
        var snpPath = this.down('#snpPath').getValue();
        if (!snpPath) {
            Ext4.Msg.alert('Error', 'Must enter the name of the NCBI organism/build subdirectory');
            return;
        }

        var genomeId = this.down('#genomeId').getValue();
        if (!genomeId) {
            Ext4.Msg.alert('Error', 'Must enter the base genome');
            return;
        }

        Ext4.Msg.wait('Saving...');
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('variantdb', 'loadDbSnpData'),
            jsonData: {
                snpPath: snpPath,
                genomeId: genomeId
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