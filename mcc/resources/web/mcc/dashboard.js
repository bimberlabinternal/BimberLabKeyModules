var MCC = {};

MCC.Dashboard = new function() {
    return {
        loadData: function () {
            LABKEY.Query.selectRows({
                schemaName: 'study',
                queryName: 'demographics',
                columns: 'Id,birth,death,gender,species,Id/age/AgeFriendly',
                success: function(results) {
                    console.log(results.rows);
                },
                error: LDK.Utils.getErrorCallback(),
                scope: this
            });
        }
    }
};
