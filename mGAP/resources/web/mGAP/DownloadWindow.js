Ext4.define('mGAP.window.DownloadWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(releaseId){
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
            columns: 'objectId,vcfId/dataid/Name,vcfId/library_id/fasta_file/Name,sitesOnlyVcfId/dataid/Name',
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

                LDK.Assert.assertNotEmpty('Missing objectId variantCatalogReleases', results.rows[0].objectId);

                var releaseVcfWithGUID = results.rows[0].objectId + '/' + results.rows[0]['vcfId/dataid/Name'];
                var sitesOnlyVcfWithGUID = results.rows[0].objectId + '/' + results.rows[0]['sitesOnlyVcfId/dataid/Name'];
                var genomeFasta = results.rows[0]['vcfId/library_id/fasta_file/Name'];
                var genomeDict = genomeFasta.replace(/fasta$/, 'dict')

                const getFileName = function(path){
                    path = path.split('/');
                    return(path[path.length - 1]);
                }

                var toAdd = [{
                    html: 'Due to the large file size, the preferred option is to download using wget or curl on the command line, such as the exmaples below. Nonetheless, you also are able to paste the URLs into your browser and download through this way as well, although it will be slower and possibly not able to resume if your connection is disrupted.<br><br>' +
                            'Use these to download the VCF and index. Note, -C will allow file resume if the download is disrupted:<br>' +
                            '<pre>curl -C - -o ' + getFileName(releaseVcfWithGUID) + ' https://mgapdownload.ohsu.edu/' + releaseVcfWithGUID + '<br>' +
                            'curl -C - -o ' + getFileName(releaseVcfWithGUID) + '.tbi' + ' https://mgapdownload.ohsu.edu/' + releaseVcfWithGUID + '.tbi</pre>' +
                            (sitesOnlyVcfWithGUID ?
                            'or a VCF without genotypes (considerably smaller):<br>' +
                            '<pre>curl -C - -o ' + getFileName(sitesOnlyVcfWithGUID) + ' https://mgapdownload.ohsu.edu/' + sitesOnlyVcfWithGUID + '<br>' +
                            'curl -C - -o ' + getFileName(sitesOnlyVcfWithGUID) + '.tbi' + ' https://mgapdownload.ohsu.edu/' + sitesOnlyVcfWithGUID + '.tbi</pre>' : '') +
                            'and genome:<br>' +
                            '<pre>curl -C - -o ' + getFileName(genomeFasta) + ' https://mgapdownload.ohsu.edu/genomes/' + genomeFasta + '<br>' +
                            'curl -C - -o ' + getFileName(genomeFasta) + '.fai' + ' https://mgapdownload.ohsu.edu/genomes/' + genomeFasta + '.fai<br>' +
                            'curl -C - -o ' + getFileName(genomeDict) + ' https://mgapdownload.ohsu.edu/genomes/' + genomeDict + '</pre>'
                },{
                    html: '<br><b>mGAP is an NIH funded project.  If you use these data in a publication, we ask that you please include R24 OD021324 in the acknowledgements.</b>',
                    border: false,
                    style: 'padding-bottom: 20px;'
                }];

                this.removeAll();
                this.add(toAdd);
            }
        });
    }
});