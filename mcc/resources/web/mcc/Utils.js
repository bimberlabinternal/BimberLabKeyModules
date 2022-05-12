Ext4.namespace('MCC');

MCC.Utils = new function() {
    return {
        /**
         * Returns the value for the MCC containerPath on this server.  If the property has not been set, and if the Id of a element
         * is provided,  it will write a message to that element.
         * @returns {Object}
         */
        getMCCContext: function(msgTarget, requiredProps){
            var ctx = LABKEY.getModuleContext('mcc');
            var requiredProps = requiredProps || ['MCCContainer', 'MCCRequestContainer'];
            var missingProps = [];
            for (var i=0;i<requiredProps.length;i++){
                if (!ctx[requiredProps[i]]) {
                    missingProps.push(requiredProps[i]);
                }
                else {
                    if (!ctx[requiredProps[i]].startsWith('/')) {
                        ctx[requiredProps[i]] = '/' + ctx[requiredProps[i]];
                    }

                    if (ctx[requiredProps[i]].endsWith('/')) {
                        ctx[requiredProps[i]] = ctx[requiredProps[i]].substr(0, -1);
                    }
                }
            }

            if (missingProps.length > 0){
                if (msgTarget)
                    Ext4.get(msgTarget).update('The following module properties for the MCC have not been set: ' + missingProps.join(', ') + '.  Please ask your administrator to configure this under the folder settings page.');
                return null;
            }

            return ctx;
        },

        isRequestAdmin: function(){
            var ctx = LABKEY.getModuleContext('mcc');

            return !!ctx.hasRequestAdminPermission;
        },

        hasRabPermission: function(){
            var ctx = LABKEY.getModuleContext('mcc');

            return !!ctx.hasRabPermission;
        },

        hasFinalDecisionPermission: function(){
            var ctx = LABKEY.getModuleContext('mcc');

            return !!ctx.hasFinalDecisionPermission;
        }
    }
};