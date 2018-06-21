Ext4.define('TCRdb.window.DownloadCloneWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];

            Ext4.Msg.wait('Loading...');
            dataRegion.getSelected({
                success: function(data, response){
                    Ext4.Msg.hide();
                    var keys = [];
                    Ext4.Array.forEach(data.selected, function(d){
                        d = d.split(',');
                        keys = keys.concat(d);
                    }, this);
                    keys = Ext4.Array.unique(keys);

                    if (!keys.length){
                        Ext4.Msg.alert('Error', 'No Rows Selected');
                        return;
                    }

                    var win = Ext4.create('TCRdb.window.DownloadCloneWindow', {
                        dataRegionName: dataRegionName,
                        selected: keys
                    }).show();
                },
                failure: LDK.Utils.getErrorCallback(),
                scope: this
            });
        }
    },

    initComponent: function(){
        Ext4.QuickTips.init();
        Ext4.apply(this, {
            width: 500,
            modal: true,
            title: 'Export Data For Clones',
            bodyStyle: 'padding: 5px;',
            items: [{
                html: 'The goal of this is to download a ZIP with any extracted clone/read data for the selected sample(s), along with the reference sequence for the segments used.  These files can be imported into Geneious or a similar program to de novo assemble to construct the FL clone.  Note: per sample, it will export all reads overlapping any TCR segments.  This at minimum will tend to include both chains (i.e. 2 different CDR3s), and might include reads that either match a defunct TCR or other noise.',
                border: false,
                style: 'padding-bottom: 10px;'
            }],
            buttons: [{
                text: 'Submit',
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

    onSubmit: function(){
        var dataRegion = LABKEY.DataRegions[this.dataRegionName];
        var newForm = document.createElement('form');
        newForm.method = 'post';
        newForm.action = LABKEY.ActionURL.buildURL('tcrdb', 'downloadCloneMaterials');
        document.body.appendChild(newForm);

        var csrfElement = document.createElement('input');
        csrfElement.setAttribute('name', 'X-LABKEY-CSRF');
        csrfElement.setAttribute('type', 'hidden');
        csrfElement.setAttribute('value', LABKEY.CSRF);
        newForm.appendChild(csrfElement);

        Ext4.Array.forEach(this.selected, function (s) {
            var newElement = document.createElement('input');
            newElement.setAttribute('name', 'rowId');
            newElement.setAttribute('type', 'hidden');
            newElement.setAttribute('value', s);
            newForm.appendChild(newElement);
        }, this);

        var newElement = document.createElement('input');
        newElement.setAttribute('name', 'schemaName');
        newElement.setAttribute('type', 'hidden');
        newElement.setAttribute('value', dataRegion.schemaName);
        newForm.appendChild(newElement);

        var newElement2 = document.createElement('input');
        newElement2.setAttribute('name', 'queryName');
        newElement2.setAttribute('type', 'hidden');
        newElement2.setAttribute('value', dataRegion.queryName);
        newForm.appendChild(newElement2);

        newForm.submit();
    }
});