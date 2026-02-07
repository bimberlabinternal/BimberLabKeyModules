Ext4.ns('GenotypeAssays');

GenotypeAssays.buttons = new function(){
    return {
        sbtReviewHandler: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length){
                alert('No records selected');
                return;
            }

            window.location = LABKEY.ActionURL.buildURL('genotypeassays', 'sbtReview', null, {analysisIds: checked.join(';')});
        },

        haplotypeHandler: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length){
                alert('No records selected');
                return;
            }

            if (checked.length !== 1) {
                alert('Only one row at a time can be selected');
                return;
            }

            var newForm = Ext4.DomHelper.append(document.getElementsByTagName('body')[0],
                    '<form method="POST" action="' + LABKEY.ActionURL.buildURL("genotypeassays", "bulkHaplotype", null) + '">' +
                    '<input type="hidden" name="analysisId" value="' + Ext4.htmlEncode(checked[0]) + '" />' +
                    '</form>');
            newForm.submit();
        }
    }
};
