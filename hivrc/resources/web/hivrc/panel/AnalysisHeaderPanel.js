/**
 * @param description
 * @param materials
 * @param methods
 * @param results
 */
Ext4.define('HIVRC.panel.AnalysisHeaderPanel', {
    extend: 'Laboratory.panel.WorkbookHeaderPanel',
    panelTitle: 'Analysis Details',

    getPanelItems: function() {
        return [{
            html: 'Analysis Description:',
            style: 'font-weight: bold;'
        },
            this.getFieldCfg('description', this.description)
            ,{
                html: 'Results:',
                style: 'font-weight: bold;padding-top: 10px;'
            },
            this.getFieldCfg('results', this.results)
            ,{
                html: 'Tags:',
                style: 'font-weight: bold;padding-top: 10px;'
            },
            this.getTagFieldCfg()
        ]
    },

    onUpdate: function(){
        var values = {};
        Ext4.each(['description', 'results'], function(text){
            values[text] = this.down('#' + text).getValue();
        }, this);

        values.tags = this.tags;
        values.forceTagUpdate = true;

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('laboratory', 'updateWorkbook'),
            method: 'POST',
            params: values,
            failure: LDK.Utils.getErrorCallback()
        });
    }
});