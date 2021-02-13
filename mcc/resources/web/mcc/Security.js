Ext4.namespace('MCC.Security');

MCC.Security = new function(){
    return {
        approveUserRequests: function(dataRegionName){
            var dr = LABKEY.DataRegions[dataRegionName];
            if (!dr){
                alert('Unable to find DataRegion with name: ' + dataRegionName);
                return;
            }

            var rowIds = dr.getChecked();
            if (!rowIds.length){
                alert('Must select one or more rows');
                return;
            }

            Ext4.Msg.confirm('Approve Requests', 'You are able to approve ' + rowIds.length + ' user requests.  Continue?', function(val){
                if (val === 'yes'){
                    Ext4.Msg.wait('Loading...');
                    LABKEY.Ajax.request({
                        method: 'POST',
                        url: LABKEY.ActionURL.buildURL('mcc', 'approveUserRequests'),
                        params: {
                            requestIds: rowIds
                        },
                        success: function(){
                            Ext4.Msg.hide();
                            Ext4.Msg.alert('Success', 'Requests approved!', function(){
                                //note: drop view, so we see the newly added user(s)
                                LABKEY.DataRegions[dataRegionName].changeView(null);
                            });
                        },
                        failure: LDK.Utils.getErrorCallback({
                            showAlertOnError: false,
                            scope: this,
                            callback: function(responseObj){
                                if (responseObj.errorMsg){
                                    Ext4.Msg.alert('Error', responseObj.errorMsg);
                                }
                            }
                        })
                    });

                }
            }, this);
        }
    }
};