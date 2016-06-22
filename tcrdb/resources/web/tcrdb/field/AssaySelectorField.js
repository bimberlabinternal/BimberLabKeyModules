Ext4.define('TCRdb.field.AssaySelectorField', {
	extend: 'Ext.form.field.ComboBox',
	alias: 'widget.tcr-assayselectorfield',

	initComponent: function(){
		Ext4.apply(this, {
			queryMode: 'local',
			triggerAction: 'all',
			store: {
				type: 'array',
				proxy: {
					type: 'memory'
				},
				fields: [{name: 'RowId', type: 'int'}, {name: 'Name'}]
			},
			valueField: 'RowId',
			displayField: 'Name',
			forceSelection: true,
			disabled: true
		});

		this.callParent(arguments);


		LABKEY.Assay.getByType({
			type: 'TCRdb',
			containerPath: Laboratory.Utils.getQueryContainerPath(),
			scope: this,
			failure: LDK.Utils.getErrorCallback(),
			success: this.onLoad
		});
	},

	onLoad: function(results){
		if (!results || !results.length){
			return;
		}

		Ext4.Array.forEach(results, function(r){
			this.store.add(this.store.createModel({
				RowId: r.id,
				Name: r.name
			}));
		}, this);

		if (results.length == 1){
			this.setValue(results[0].id);
		}
		this.setDisabled(false);
	}
});