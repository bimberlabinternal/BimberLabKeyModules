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
        },

        deleteDuplicateReferenceItems: function(dataRegionName) {
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length){
                alert('No records selected');
                return;
            }

            Ext4.Msg.alert('Delete Selected', 'Are you sure you want to delete the selected ' + checked.length + ' records?', function(btn){
                Ext4.Msg.wait('Deleting...');

                var toDelete = [];
                Ext4.Array.forEach(checked, function(r){
                    toDelete.push({rowId: r});
                }, this);

                LABKEY.Query.deleteRows({
                    schemaName: 'labpurchasing',
                    queryName: 'referenceItems',
                    rows: toDelete,
                    scope: this,
                    failure: LDK.Utils.getErrorCallback(),
                    success: function(){
                        Ext4.Msg.hide();
                        Ext4.Msg.alert('Success', 'Rows deleted', function(){
                            var dataRegion = LABKEY.DataRegions[dataRegionName];
                            dataRegion.refresh();
                        });
                    }
                })
            });
        },

        addToReferenceItems: function(dataRegionName){
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length){
                alert('No records selected');
                return;
            }

            Ext4.Msg.alert('Add Selected', 'Are you sure you want to add the selected ' + checked.length + ' item(s) to the reference items table?', function(btn){
                Ext4.Msg.wait('Adding Items...');

                LABKEY.Query.selectRows({
                    schemaName: 'labpurchasing',
                    queryName: 'purchases',
                    columns: 'rowId,vendorId,itemName,itemNumber,units,unitCost',
                    filterArray: [LABKEY.Filter.create('rowId', checked.join(';'), LABKEY.Filter.Types.IN)],
                    scope: this,
                    failure: LDK.Utils.getErrorCallback(),
                    success: function(results){
                        var toInsert = [];
                        Ext4.Array.forEach(results.rows, function(r){
                            toInsert.push({
                                vendorId: r.vendorId,
                                itemName: r.itemName,
                                itemNumber: r.itemNumber,
                                units: r.units,
                                unitCost: r.unitCost
                            });
                        }, this);

                        LABKEY.Query.insertRows({
                            schemaName: 'labpurchasing',
                            queryName: 'referenceItems',
                            rows: toInsert,
                            scope: this,
                            failure: LDK.Utils.getErrorCallback(),
                            success: function() {
                                Ext4.Msg.hide();
                                Ext4.Msg.alert('Success', 'Items Added', function () {
                                    var dataRegion = LABKEY.DataRegions[dataRegionName];
                                    dataRegion.refresh();
                                }, this);
                            }
                        });
                    }
                });
            });
        },

        excludeFromReferenceItems: function(dataRegionName) {
            var dataRegion = LABKEY.DataRegions[dataRegionName];
            var checked = dataRegion.getChecked();
            if (!checked || !checked.length){
                alert('No records selected');
                return;
            }

            Ext4.Msg.alert('Exclude Selected', 'Are you sure you want to exclude the selected ' + checked.length + ' records from reference items?', function(btn){
                Ext4.Msg.wait('Saving...');

                var toUpdate = [];
                Ext4.Array.forEach(checked, function(r){
                    toUpdate.push({
                        rowId: r,
                        excludeFromRefItems: true
                    });
                }, this);

                LABKEY.Query.updateRows({
                    schemaName: 'labpurchasing',
                    queryName: 'purchases',
                    rows: toUpdate,
                    scope: this,
                    failure: LDK.Utils.getErrorCallback(),
                    success: function(){
                        Ext4.Msg.hide();
                        Ext4.Msg.alert('Success', 'Rows updated', function(){
                            var dataRegion = LABKEY.DataRegions[dataRegionName];
                            dataRegion.refresh();
                        });
                    }
                })
            });
        }
    }
};