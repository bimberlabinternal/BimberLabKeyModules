Ext4.define('TCRDB.field.LibraryField', {
	extend: 'Ext.ux.CheckCombo',
	alias: 'widget.tcr-libraryfield',
	
	initComponent: function(){
		Ext4.apply(this, {
			store: {
				type: 'labkey-store',
				containerPath: Laboratory.Utils.getQueryContainerPath(),
				schemaName: 'tcrdb',
				queryName: 'mixcr_libraries',
				columns: 'rowid,species,locus,local,additionalParams',
				autoLoad: true,
				listeners: {
					load: function(s){
						s.each(function(r){
							r.set('displayField', r.get('label') || r.get('species'));
						})
					}
				}
			},
			valueField: 'rowid',
			displayField: 'displayField',
			forceSelection: true,
			multiSelect: true
		});
		
		this.callParent(arguments);
	},

	getValue: function(){
		var val = this.callParent(arguments);
		var ret = [];
		if (val && val.length){
			Ext4.each(val, function(idx){
				var rec = this.store.getAt(this.store.findExact('rowid', idx));
				ret.push({
					rowid: rec.get('rowid'),
					locus: rec.get('locus'),
					species: rec.get('species'),
					local: rec.get('local'),
					additionalParams: rec.get('additionalParams')
				});
			}, this);
		}

		return Ext4.encode(ret);
	}
});