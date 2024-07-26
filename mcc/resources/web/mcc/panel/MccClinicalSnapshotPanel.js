EHR.reports.clinicalHistoryPanelXtype = 'mcc-snapshotpanel';

Ext4.define('MCC.panel.SnapshotPanel', {
    extend: 'EHR.panel.SnapshotPanel',
    alias: 'widget.mcc-snapshotpanel',

    showLocationDuration: false,

    minWidth: 800,

    initComponent: function () {

        this.defaultLabelWidth = 120;
        this.callParent();
    },

    getBaseItems: function(){
        return [{
            xtype: 'container',
            border: false,
            defaults: {
                border: false
            },
            items: [{
                xtype: 'container',
                html: '<b>Summary:</b><hr>'
            },{
                bodyStyle: 'padding-bottom: 20px;',
                layout: 'column',
                defaults: {
                    border: false
                },
                items: [{
                    xtype: 'container',
                    columnWidth: 0.25,
                    defaults: {
                        labelWidth: this.defaultLabelWidth,
                        style: 'margin-right: 20px;'
                    },
                    items: [{
                        xtype: 'displayfield',
                        fieldLabel: 'Status',
                        name: 'calculated_status'
                    },{
                        xtype: 'displayfield',
                        fieldLabel: 'Gender',
                        name: 'gender'
                    },{
                        xtype: 'displayfield',
                        fieldLabel: 'Species',
                        name: 'species'
                    },{
                        xtype: 'displayfield',
                        fieldLabel: 'Dam',
                        name: 'dam'
                    },{
                        xtype: 'displayfield',
                        fieldLabel: 'Sire',
                        name: 'sire'
                    }]
                },{
                    xtype: 'container',
                    columnWidth: 0.25,
                    defaults: {
                        labelWidth: this.defaultLabelWidth,
                        style: 'margin-right: 20px;'
                    },
                    items: [{
                        xtype: 'displayfield',
                        fieldLabel: 'Age',
                        name: 'age'
                    }, {
                        xtype: 'displayfield',
                        fieldLabel: 'Current Colony',
                        name: 'colony'
                    }, {
                        xtype: 'displayfield',
                        fieldLabel: 'Source Colony',
                        name: 'source'
                    },{
                        xtype: 'displayfield',
                        fieldLabel: 'Littermates',
                        name: 'littermates'
                    },{
                        xtype: 'displayfield',
                        fieldLabel: 'Weights',
                        name: 'weights'
                    }]
                }]
            }]
        }];
    },

    appendDemographicsResults: function(toSet, row, id){
        this.callParent(arguments);

        toSet['dam'] = row.getDam()
        toSet['sire'] = row.getSire()
        toSet['colony'] = row.getData().colony
        toSet['source'] = row.getData().source
        toSet['littermates'] = row.getData().litterMates
    }
});