Ext4.define('mGAP.window.DownloadWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(releaseId, el){
            Ext4.create('mGAP.window.DownloadWindow', {
                releaseId: releaseId
            }).show(el)
        }
    },

    initComponent: function() {
        Ext4.apply(this, {
            title: 'Download Release',
            bodyStyle: 'padding: 5px;',
            width: '80%',
            defaults: {
                border: false
            },
            items: [{
                html: 'Loading...'
            }],
            buttons: [{
                text: 'Close',
                handler: function (btn) {
                    btn.up('window').close();
                }
            }]
        });

        this.callParent(arguments);

        this.loadData();
    },

    loadData: function() {
        Ext4.Msg.wait('Loading...');
        LABKEY.Query.selectRows({
            method: 'POST',
            schemaName: 'mgap',
            queryName: 'variantCatalogReleases',
            columns: 'vcfId/dataid/Name,vcfId/library_id/fasta_file/Name,sitesOnlyVcfId/dataid/Name',
            filterArray: [LABKEY.Filter.create('rowid', this.releaseId, LABKEY.Filter.Types.EQUAL)],
            scope: this,
            failure: LDK.Utils.getErrorCallback(),
            success: function (results) {
                Ext4.Msg.hide();

                if (!results || !results.rows || !results.rows.length) {
                    Ext4.Msg.alert('Error', 'Unable to find matching rows');
                    return;
                }

                var releaseVcf = LABKEY.ActionURL.getBaseURL(true) + results.rows[0]['vcfId/dataid/Name'];
                var urlFasta = LABKEY.ActionURL.getBaseURL(true) + results.rows[0]['vcfId/library_id/fasta_file/Name'];
                var sitesOnlyVcf = LABKEY.ActionURL.getBaseURL(true) + results.rows[0]['sitesOnlyVcfId/dataid/Name'];

                var toAdd = [{
                    html: 'Due to the large file size, the preferred option is to download using wget or curl on the command line. Nonetheless, you also are able to paste the URLs into your browser and download through this way as well, although it will be slower and possibly not able to resume if your connection is disrupted.<br><br>' +
                            'mGAP is an NIH funded project.  If you use these data in a publication, we ask that you please include R24OD021324 in the acknowledgements.',
                    border: false,
                    style: 'padding-bottom: 20px;'
                },{
                    html: 'Either wget or curl can be used to download the release VCF, similar to the commands below:<br><br>' +
                            'wget https://mgapdownload.ohsu.edu/' + releaseVcf + '<br><br>' +
                            'wget https://mgapdownload.ohsu.edu/' + releaseVcf + '.tbi<br><br>' +
                            'or a VCF without genotypes (considerably smaller):<br><br>' +
                            'wget https://mgapdownload.ohsu.edu/' + sitesOnlyVcf + '<br><br>' +
                            'wget https://mgapdownload.ohsu.edu/' + sitesOnlyVcf + '.tbi<br><br>' +
                            'and genome:<br><br>' +
                            'wget https://mgapdownload.ohsu.edu/' + urlFasta + '<br>' +
                            'wget https://mgapdownload.ohsu.edu/' + urlFasta + '.fai<br>' +
                            'wget https://mgapdownload.ohsu.edu/' + urlFasta.replace(/fasta$/, 'dict')
                }];

                this.removeAll();
                this.add(toAdd);
            }
        });
    }
});