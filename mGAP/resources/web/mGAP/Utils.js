window.mGAP = window.mGAP || {};

mGAP.Utils = (function($){


    return {
        renderReleaseGraph: function(targetId1, targetId2, width){
            Plotly.newPlot(targetId1, [{
                    "autobinx": true,
                    "uid": "13ab10",
                    "name": "B",
                    "labels": ["Other", "Missense", "Synonymous", "3' UTR", "Downstream Gene", "Intragenic", "Upstream Gene", "Intergenic", "Intron"],
                    "values": ["73134", "83390", "107376", "137443", "446880", "537483", "1387612", "7516786", "5224810"],
                    "mode": "markers",
                    "marker": {"colors": ["rgb(255, 255, 204)", "rgb(161, 218, 180)", "rgb(65, 182, 196)", "rgb(44, 127, 184)", "rgb(8, 104, 172)", "rgb(37, 52, 148)"]},
                    "textinfo": "label+percent",
                    "type": "pie",
                    "autobiny": true
                }], {
                    //"title": "Breakdown of Coding Potential",
                    "width": width,
                    "height": 200,
                    "margin": {"l": 80, "r": 0, "t": 20, "b": 90},
                    "autosize": false,
                    "showlegend": false,
                    "breakpoints": [],
                    "titlefont": {"size": 14},
                    "hovermode": "closest",
                    "font": {"size": 12},
                    "legend": {"font": {"size": 12}}
                }, {displayModeBar: false});

            Plotly.newPlot(targetId2, [{
                    "autobinx": true,
                    "name": "# Variants",
                    "mode": "markers",
                    "x": ["9178", "61910", "20176", "142189", "8045", "25945", "1973"],
                    "y": ["GWAS Associations<br>(GRASP)", "Enhancer Region<br>(FANTOM5)", "Predicted Enhancer<br>(ENCODE)", "Transcription Factor<br>Binding (ENCODE)", "Predicted High<br>Impact (SnpEff)", "Damaging<br>(Polyphen2)", "ClinVar Overlap"],
                    "type": "bar",
                    "orientation": "h",
                    "autobiny": true
                }], {
                    //"title": "Summary of Annotations",
                    "autosize": false,
                    "width": width,
                    "height": 400,
                    "margin": {"l": 150, "r": 0, "t": 0, "b": 40},
                    "breakpoints": [],
                    "hovermode": "closest",
                    "yaxis": {"tickfont": {"size": 12}, "title": "", "range": [-0.5, 6.5], "titlefont": {"size": 12}, "type": "category", "autorange": true},
                    "xaxis": {"tickfont": {"size": 12}, "title": "# Variants", "range": [0, 149672.63157894736], "titlefont": {"size": 12}, "type": "linear", "autorange": true}},
            {displayModeBar: false});
        },

        getMGapJBrowseSession: function(){
            var ctx = LABKEY.getModuleContext('mgap') || {};

            return ctx['mgapJBrowse'];
        }
    }
})(jQuery);