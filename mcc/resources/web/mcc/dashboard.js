var MCC = {};

MCC.Dashboard = new function() {
    return {
        loadDataAndRender: function (wrapperDivId) {
            LABKEY.Query.selectRows({
                schemaName: 'study',
                queryName: 'demographics',
                columns: 'Id,birth,death,gender,species,Id/age/AgeFriendly',
                success: function(results) {
                    console.log(results.rows);
                },
                failure: function(response) {
                    alert('It didnt work!');
                },
                scope: this
            });
        }
    }
};
