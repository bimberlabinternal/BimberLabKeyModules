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
				columns: 'rowid,label,libraryName,species,locus,additionalParams',
				sort: 'label,libraryName',
				filterArray: [LABKEY.Filter.create('dateDisabled', null, LABKEY.Filter.Types.ISBLANK)],
				autoLoad: true,
				listeners: {
					load: function(s){
						s.each(function(r){
							r.set('displayField', r.get('label') || r.get('libraryName'));
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
				if (!rec){
					return;
				}

				ret.push({
					rowid: rec.get('rowid'),
					locus: rec.get('locus'),
					species: rec.get('species'),
					libraryName: rec.get('libraryName'),
					additionalParams: rec.get('additionalParams')
				});
			}, this);
		}

		return Ext4.encode(ret);
	},

	setValue: function(val){
		if (this.store && this.store.isLoading()){
			var args = arguments;
			this.store.on('load', function(){
				this.setValue.apply(this, args);
			}, this, {defer: 100, single: true});

			return;
		}

		if (val){
			try {
				// this is kinda hacky.  the purpose is to enable saved templates to apply their saved value, but we
				// need to be compatible with the regular setValue() code paths
				if (Ext4.isString(arguments[0])) {
					var json = eval(arguments[0]);
					if (Ext4.isArray(json)) {
						var rowIds = [];
						Ext4.Array.forEach(json, function (row)
						{
							rowIds.push(row.rowid);
						}, this);
						arguments[0] = rowIds;
					}
				}
			} catch (e) {
				//ignore
			}
		}

		this.callParent(arguments);
	}
});