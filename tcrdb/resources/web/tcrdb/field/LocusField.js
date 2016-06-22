Ext4.define('TCRDB.field.LocusField', {
	extend: 'Ext.ux.CheckCombo',
	alias: 'widget.tcrdb-locusfield',
	
	initComponent: function(){
		Ext4.apply(this, {
			store: {
				type: 'labkey-store',
				containerPath: Laboratory.Utils.getQueryContainerPath(),
				schemaName: 'tcrdb',
				queryName: 'loci',
				columns: 'locus',
				autoLoad: true,
				listeners: {
					scope: this,
					load: function(s){
						if (this.value && !Ext4.isArray(this.value)){
							this.value = this.value.split(';');
						}

						this.setValue(this.value);
					}
				}
			},
			valueField: 'locus',
			displayField: 'locus',
			forceSelection: true,
			multiSelect: true,
			allowBlank: false
		});
		
		this.callParent(arguments);
	},

	getValue: function(){
		var val = this.callParent(arguments);
		var ret = [];
		if (val && val.length){
			return val.join(';')
		}

		return Ext4.encode(ret);
	}
});