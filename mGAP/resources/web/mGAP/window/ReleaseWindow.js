Ext4.define('mGAP.window.ReleaseWindow', {
    extend: 'SequenceAnalysis.window.OutputHandlerWindow',

    statics: {
        buttonHandler: function (dataRegionName) {
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var handlerClass = 'org.labkey.mgap.pipeline.mGapReleaseGenerator';

            //first validate
            Ext4.Msg.wait('Loading tracks...');
            LABKEY.Query.selectRows({
                schemaName: 'mgap',
                queryName: 'releaseTracks',
                scope: this,
                columns: 'vcfId,trackName,vcfId/library_id,isprimarytrack',
                failure: LDK.Utils.getErrorCallback(),
                success: function (results) {
                    Ext4.Msg.hide();
                    var outputFiles = [];
                    var distinctGenomes = [];
                    Ext4.Array.forEach(results.rows, function(r){
                        if (r.vcfId) {
                            outputFiles.push(r.vcfId);

                            if (r['vcfId/library_id']) {
                                distinctGenomes.push(r['vcfId/library_id']);
                            }
                        }
                        else if (!r['isprimarytrack']) {
                            console.error('Track lacks VCF ID: ' + r.trackName);
                        }
                    }, this);

                    if (!outputFiles.length){
                        Ext4.Msg.alert('Error', 'None of the tracks have VCF files');
                        return;
                    }

                    distinctGenomes = Ext4.Array.unique(distinctGenomes);
                    if (distinctGenomes.length !== 1){
                        Ext4.Msg.alert('Error', 'All files must use the same genome.  Genomes found: ' + distinctGenomes.length);
                    }

                    LABKEY.Ajax.request({
                        method: 'POST',
                        url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'checkFileStatusForHandler'),
                        params: {
                            handlerType: 'OutputFile',
                            handlerClass: handlerClass,
                            outputFileIds: outputFiles
                        },
                        scope: this,
                        failure: LABKEY.Utils.getCallbackWrapper(LDK.Utils.getErrorCallback(), this),
                        success: LABKEY.Utils.getCallbackWrapper(function(results) {
                            Ext4.Msg.hide();

                            Ext4.create('Laboratory.window.WorkbookCreationWindow', {
                                title: 'Create New Workbook or Add To Existing?',
                                workbookPanelCfg: {
                                    doLoad: function (containerPath) {
                                        Ext4.create('SequenceAnalysis.window.OutputHandlerWindow', {
                                            containerPath: containerPath,
                                            dataRegionName: dataRegionName,
                                            handlerType: 'OutputFile',
                                            handlerClass: handlerClass,
                                            outputFileIds: outputFiles,
                                            outputFileMap: results.outputFiles,
                                            title: results.name,
                                            handlerConfig: results,
                                            toolParameters: results.toolParameters,
                                            libraryId: distinctGenomes.length == 1 ? distinctGenomes[0] : null
                                        }).show();
                                    }
                                }
                            }).show();
                        })
                    });
                }
            });
        },

        initComponent: function () {
            this.callParent(arguments);
        }
    }
});