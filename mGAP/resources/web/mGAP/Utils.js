window.mGAP = window.mGAP || {};

mGAP.Utils = (function($){


    return {
        renderReleaseGraph: function(outerDiv, width){
            var targetId1 = 'mgap-release-graph1', targetId2 = 'mgap-release-graph2', tableId = 'mgap-release-table';

            if (!mGAP.Utils.getMGapReleaseId()){
                $(targetId1).html('<span>No release information</span>');
                return;
            }

            LABKEY.Query.selectRows({
                schemaName: 'mgap',
                queryName: 'releaseStats',
                failure: LDK.Utils.getErrorCallback(),
                scope: this,
                success: function(results){
                    var map = {};
                    $.each(results.rows, function(idx, val){
                        if (!map[val.category]){
                            map[val.category] = {};
                        }

                        map[val.category][val.metricName] = val.value;
                    });

                    function numberWithCommas(number) {
                        if (!number){
                            return '';
                        }

                        var parts = number.toString().split(".");
                        parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ",");
                        return parts.join(".");
                    }

                    var data0 = map.Counts || {};
                    $('#' + tableId).html('<table><tr><td>Total Variants:</td><td>' + numberWithCommas(data0.TotalVariants) + '</td></tr><tr><td>Total Animals:</td><td>' + numberWithCommas(data0.TotalSamples) + '</td></tr><tr><td style="padding-right: 20px;">Private Variants:</td><td>' + numberWithCommas(data0.TotalPrivateVariants) + '</td></tr></table>');

                    var data1 = map.CodingPotential || {};
                    var codingLabels = ["Missense", "Synonymous", "3' UTR", "5' UTR", "Downstream Gene", "Intragenic", "Upstream Gene", "Intergenic", "Intron"];
                    var codingData = [];
                    var codingDataMap = {};
                    for (var metricName in data1){
                        var target;
                        switch (metricName) {
                            case 'missense_variant':
                                target = 'Missense';
                                break;
                            case 'synonymous_variant':
                                target = 'Synonymous';
                                break;
                            case '3_prime_UTR_variant':
                                target = '3\' UTR';
                                break;
                            case '5_prime_UTR_premature_start_codon_gain_variant':
                            case '5_prime_UTR_variant':
                                target = '5\' UTR';
                                break;
                            case 'downstream_gene_variant':
                                target = 'Downstream Gene';
                                break;
                            case 'intragenic_variant':
                                target = 'Intragenic';
                                break;
                            case 'intergenic_region':
                                target = 'Intergenic';
                                break;
                            case 'intron_variant':
                                target = 'Intron';
                                break;
                            default:
                                target = metricName;

                            //other examples:
                            //case 'stop_lost':
                            //case 'splice_acceptor_variant':
                            //case 'splice_region_variant':
                            //case 'stop_retained_variant':
                            //case 'non_coding_transcript_exon_variant':
                            //case 'stop_gained':
                            //case 'initiator_codon_variant':
                            //case 'non_coding_transcript_variant':
                            //case 'start_lost':
                            //case 'non_canonical_start_codon':
                            //case 'splice_donor_variant':
                        }

                        codingDataMap[target] = codingDataMap[target] || 0;
                        codingDataMap[target] += data1[metricName];
                    }

                    $.each(codingLabels, function(idx, val){
                        codingData.push(codingDataMap[val] || 0);
                    });

                    Plotly.newPlot(targetId1, [{
                            "autobinx": true,
                            "uid": "13ab10",
                            "name": "B",
                            //TODO
                            "labels": codingLabels,
                            "values": codingData,
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

                    var data2 = map.AnnotationSummary || {};
                    var annotationData = [];
                    annotationData.push(data2['GWAS Associations (GRASP)'] || 0);
                    annotationData.push(data2['Enhancer Region (FANTOM5)'] || 0);
                    annotationData.push(data2['Predicted Enhancer (ENCODE)'] || 0);
                    annotationData.push(data2['Transcription Factor Binding (ENCODE)'] || 0);
                    annotationData.push(data2['Predicted High Impact (SnpEff)'] || 0);
                    annotationData.push(data2['Damaging (Polyphen2)'] || 0);
                    annotationData.push(data2['ClinVar Overlap'] || 0);
                    Plotly.newPlot(targetId2, [{
                        "autobinx": true,
                        "name": "# Variants",
                        "mode": "markers",
                        "x": annotationData,
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
                }
            })
        },

        getMGapJBrowseSession: function(){
            var ctx = LABKEY.getModuleContext('mgap') || {};

            return ctx['mgapJBrowse'];
        },

        getMGapReleaseId: function(){
            var ctx = LABKEY.getModuleContext('mgap') || {};

            return ctx['mgapReleaseId'];
        }
    }
})(jQuery);