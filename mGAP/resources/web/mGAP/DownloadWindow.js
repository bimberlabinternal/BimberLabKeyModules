Ext4.define('mGAP.window.DownloadWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(releaseId, el){
            Ext4.create('mGAP.window.DownloadWindow', {
                releaseId: releaseId
            });
        }
    },

    initComponent: function() {
        Ext4.apply(this, {
            title: 'Download Release',
            bodyStyle: 'padding: 5px;',
            width: '800px',
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
                this.show();

                if (!results || !results.rows || !results.rows.length) {
                    Ext4.Msg.alert('Error', 'Unable to find matching rows');
                    return;
                }

                var releaseVcf = results.rows[0]['vcfId/dataid/Name'];
                var urlFasta = results.rows[0]['vcfId/library_id/fasta_file/Name'];
                var sitesOnlyVcf = results.rows[0]['sitesOnlyVcfId/dataid/Name'];

                var toAdd = [{
                    html: 'Due to the large file size, the preferred option is to download using wget or curl on the command line, such as the exmaples below. Nonetheless, you also are able to paste the URLs into your browser and download through this way as well, although it will be slower and possibly not able to resume if your connection is disrupted.<br><br>' +
                            'Use these to download the VCF and index:<br>' +
                            '<pre>wget https://mgapdownload.ohsu.edu/' + releaseVcf + '<br>' +
                            'wget https://mgapdownload.ohsu.edu/' + releaseVcf + '.tbi</pre>' +
                            (sitesOnlyVcf ?
                            'or a VCF without genotypes (considerably smaller):<br>' +
                            '<pre>wget https://mgapdownload.ohsu.edu/' + sitesOnlyVcf + '<br>' +
                            'wget https://mgapdownload.ohsu.edu/' + sitesOnlyVcf + '.tbi</pre>' : '') +
                            'and genome:<br>' +
                            '<pre>wget https://mgapdownload.ohsu.edu/' + urlFasta + '<br>' +
                            'wget https://mgapdownload.ohsu.edu/' + urlFasta + '.fai<br>' +
                            'wget https://mgapdownload.ohsu.edu/' + urlFasta.replace(/fasta$/, 'dict') + '</pre>'
                },{
                    html: '<br><b>mGAP is an NIH funded project.  If you use these data in a publication, we ask that you please include R24OD021324 in the acknowledgements.</b>',
                    border: false,
                    style: 'padding-bottom: 20px;'
                }];

                this.removeAll();
                this.add(toAdd);
            }
        });
    }
});