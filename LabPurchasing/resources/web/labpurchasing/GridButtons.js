Ext4.ns('LabPurchasing.buttons');

LabPurchasing.buttons = new function(){
    return {
        purchaseItems: function(dataRegionName) {
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length){
                alert('No records selected');
                return;
            }

            window.location = LABKEY.ActionURL.buildURL('labpurchasing', 'placeOrders', null, {rowId: checked.join(';')});
        }
    }
};