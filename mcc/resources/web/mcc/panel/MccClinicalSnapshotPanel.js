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

    appendWeightResults: function(toSet, results){
        var text = [];
        if (results){
            var rows = [];
            var prevRow;
            Ext4.each(results, function(row){
                var newRow = {
                    weight: row.weightGrams,
                    date: LDK.ConvertUtils.parseDate(row.date)
                };

                var prevDate = prevRow ? prevRow.date : new Date();
                if (prevDate){
                    //round to day for purpose of this comparison
                    var d1 = Ext4.Date.clearTime(prevDate, true);
                    var d2 = Ext4.Date.clearTime(newRow.date, true);
                    var interval = Ext4.Date.getElapsed(d1, d2);
                    interval = interval / (1000 * 60 * 60 * 24);
                    interval = Math.floor(interval);
                    newRow.interval = interval + (prevRow ? ' days between' : ' days ago');
                }

                rows.push(newRow);
                prevRow = newRow;
            }, this);

            Ext4.each(rows, function(r){
                text.push('<tr><td nowrap>' + Ext4.util.Format.number(r.weight,'##.000')  + ' g' + '</td><td style="padding-left: 5px;" nowrap>' + Ext4.Date.format(r.date, 'Y-m-d H:i') + '</td><td style="padding-left: 5px;" nowrap>' + (Ext4.isDefined(r.interval) ? ' (' + r.interval + ')' : '') + "</td></tr>");
            }, this);
        }

        toSet['weights'] = text.length ? '<table>' + text.join('') + '</table>' : null;
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