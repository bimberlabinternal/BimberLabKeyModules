Ext4.ns('TCRdb.buttons');

TCRdb.buttons = new function(){
    return {
        createMixcrGenome: function(dataRegionName) {
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length){
                alert('No records selected');
                return;
            }
            else if (checked.length > 1) {
                alert('Can only select one genome at a time');
                return;
            }

            window.location = LABKEY.ActionURL.buildURL('tcrdb', 'createGenomeFromMixcr', null, {rowId: checked[0]});
        }
    }
};