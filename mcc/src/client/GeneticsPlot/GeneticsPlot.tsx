import React, { useEffect, useState } from 'react';
import { getServerContext, Query } from '@labkey/api';
import { TSV } from 'tsv';
import './../Dashboard/dashboard.css';
import ScatterChart from './ScatterChart';
import { ErrorBoundary } from '@labkey/components';
import { Box, Tab, Tabs } from '@material-ui/core';
import KinshipTable from './KinshipTable';

export function GeneticsPlot() {
    const [pcaData, setPcaData] = useState(null);
    const [kinshipData, setKinshipData] = useState(null);
    const [value, setValue] = React.useState(0);

    const ctx = getServerContext().getModuleContext('mcc') || {};
    const containerPath = ctx.MCCContainer || null;

    useEffect(() => {
        fetch(getServerContext().contextPath + "/mcc/data/PCA_Eigenvecs_mcc_recode_42122.txt", {
            method: 'GET'
        }).then((response) => response.text())
        .then((tsv) => {
            const data = TSV.parse(tsv)
            setPcaData(data)
        });

        Query.selectRows({
            containerPath: containerPath,
            schemaName: 'study',
            queryName: 'kinship',
            columns: 'Id,Id2,kinship,relationship',
            success: function(results) {
                setKinshipData(results.rows)
            },
            failure: function(response) {
                alert('There was an error loading data');
                console.log(response);
            },
            scope: this
        });
    }, [] /* only run the effect on mount */);

    if (!containerPath) {
        return (
            <div className="loading">
                <div>Error: must set the MCCContainer module property</div>
            </div>
        );
    }

    if (pcaData === null || kinshipData === null) {
        return (
            <div className="loading">
                <div>Loading...</div>
            </div>
        );
    }

    const handleChange = (event: React.SyntheticEvent, newValue: number) => {
        setValue(newValue);
    };

    const a11yProps = (index: number) => {
        return {
            id: `simple-tab-${index}`,
            'aria-controls': `simple-tabpanel-${index}`,
        };
    }

    return (
        <ErrorBoundary>
            <div style={{paddingBottom: 20, maxWidth: 800}}>
                Population structure analysis using PCA is a helpful way to summarize the genetic relationships among animals in the MCC. The PCA results can be thought of as a simple type of genetic clustering - animals with more similar principal component loadings are more genetically similar. A more precise description of the relationship between two animals is provided by kinship coefficients – these are quantitative measures of relatedness that can be calculated by comparing two genomes, and interpreted using genealogical language, such as ‘parent-child’, ‘uncle-nephew’, ‘first cousins’, etc.
            </div>
            <div style={{paddingBottom: 20, maxWidth: 800}}>
                Whole genome sequencing was performed on each animal and genotypes were called with GATK haplotype caller. Principal components analysis was performed with GCTA (https://yanglab.westlake.edu.cn/software/gcta/#PCA) and kinship coefficients were calculated with KING (https://www.kingrelatedness.com/). Analyses were performed by Ric del Rosario (Broad Institute)
            </div>
            <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
                <Tabs value={value} onChange={handleChange} aria-label="basic tabs example">
                    <Tab label="Population Genetic Diversity" {...a11yProps(0)} />
                    <Tab label="Kinship" {...a11yProps(1)} />
                </Tabs>
            </Box>
            <div className="row">
                <div className="col-md-6">
                    <div className="panel panel-default">
                        <div className="panel-body">
                            {value === 0 && <ScatterChart data={pcaData}/>}
                            {value === 1 && <KinshipTable data={kinshipData}/>}
                        </div>
                    </div>
                </div>
            </div>
        </ErrorBoundary>
    );
}