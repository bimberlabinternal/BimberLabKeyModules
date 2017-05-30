Ext4.define('mGAP.window.DownloadWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(releaseId, el){
            Ext4.create('mGAP.window.DownloadWindow', {
                releaseId: releaseId
            }).show(el)
        }
    },

    initComponent: function(){
        Ext4.apply(this, {
            title: 'Download Release',
            width: 600,
            bodyStyle: 'padding: 5px;',
            items: [{
                html: 'This will download a ZIP containing the selected variant release as a VCF file.  If selected, you can also include the genome FASTA, which is required to use some software.<br><br>' +
                    'mGAP is an NIH funded project.  If you use these data in a publication, we ask that you please include R24OD021324 in the acknowledgements.',
                border: false,
                style: 'padding-bottom: 20px;'
            },{
                xtype: 'checkbox',
                fieldLabel: 'Include Genome FASTA',
                labelWidth: 185,
                itemId: 'includeGenome',
                checked: false
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

    onSubmit: function(){
        var includeGenome = this.down('#includeGenome').getValue();

        Ext4.create('Ext.form.Panel', {
            url: LABKEY.ActionURL.buildURL('mgap', 'downloadBundle'),
            standardSubmit: true
        }).submit({
            params: {
                includeGenome: includeGenome,
                releaseId: this.releaseId
            }
        });

        this.close();
    }
});