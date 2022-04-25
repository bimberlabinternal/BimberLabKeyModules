Ext4.define('MCC.window.EnterReviewWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(rowId, requestId){
            Ext4.create('MCC.window.EnterReviewWindow', {
                rowId: rowId,
                requestId: requestId
            }).show();
        }
    },

    initComponent: function(){
        Ext4.apply(this, {
            modal: true,
            title: 'Enter Review',
            width: 600,
            bodyStyle: 'padding: 5px;',
            items: [{
                xtype: 'form',
                defaults: {
                    border: false,
                    width: 450
                },
                items: [{
                    xtype: 'ldk-simplecombo',
                    fieldLabel: 'Review',
                    name: 'review',
                    allowBlank: false,
                },{
                    xtype: 'textarea',
                    fieldLabel: 'Comments',
                    name: 'comments'
                }]
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

    onSubmit: function(btn){
        if (!this.down('form').isValid()){
            Ext4.Msg.alert('Error', 'There are errors in the form.  Hover over the red fields for more information.');
            return;
        }

        var record = {

        }

        //Ext4.Msg.wait('Saving...');
    }
});
