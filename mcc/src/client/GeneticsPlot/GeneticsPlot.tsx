import React, { useEffect, useState } from 'react';
import { ActionURL, Filter, getServerContext, Query } from '@labkey/api';
import './../Dashboard/dashboard.css';
import ScatterChart from './ScatterChart';
import { Box, Tab, Tabs } from '@mui/material';
import KinshipTable from './KinshipTable';
import { ErrorBoundary } from '../components/ErrorBoundary';


function GenomeBrowser(props: {jbrowseId: any}) {
    const { jbrowseId } = props;

    return (
        <div>
            <a href={ActionURL.buildURL('jbrowse', 'jbrowse', null, {session: jbrowseId})}>Click here to view Marmoset SNP data in the genome browser</a>
        </div>
    );
}

export function GeneticsPlot() {
    const [pcaData, setPcaData] = useState([]);
    const [kinshipData, setKinshipData] = useState([]);
    const [jbrowseId, setJBrowseId] = useState(null);
    const [value, setValue] = React.useState(0);

    const ctx = getServerContext().getModuleContext('mcc') || {};
    const containerPath = ctx.MCCContainer || null;

    useEffect(() => {
        Query.selectRows({
            containerPath: containerPath,
            schemaName: 'lists',
            queryName: 'PCA_Example',
            success: function(results) {
                const data = results.rows
                setPcaData(data)
            },
            failure: function(response) {
                alert('There was an error loading data');
                console.log(response);
            },
            scope: this
        });

        Query.selectRows({
            containerPath: containerPath,
            schemaName: 'jbrowse',
            queryName: 'databases',
            columns: 'objectid',
            filterArray: [
                Filter.create('name', 'Marmoset Variant Data')
            ],
            success: function(results) {
                const data = results.rows
                setJBrowseId(data.length ? data[0].objectid : null)
            },
            failure: function(response) {
                alert('There was an error loading JBrowse data');
                console.log(response);
            },
            scope: this
        });

        Query.selectRows({
            containerPath: containerPath,
            schemaName: 'study',
            queryName: 'kinship',
            columns: 'Id,Id2,kinship,relationship,objectid',
            success: function(results) {
                setKinshipData(results.rows.map((row) => {
                    return({
                        id: row.objectid,
                        Id: row.Id,
                        Id2: row.Id2,
                        kinship: row.kinship,
                        relationship: row.relationship
                    })
                }))
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
        <>
        <ErrorBoundary>
            <div style={{paddingBottom: 20, maxWidth: 1000}}>
                Population structure analysis using PCA is a helpful way to summarize the genetic relationships among animals in the MCC. The PCA results can be thought of as a simple type of genetic clustering - animals with more similar principal component loadings are more genetically similar. A more precise description of the relationship between two animals is provided by kinship coefficients – these are quantitative measures of relatedness that can be calculated by comparing two genomes, and interpreted using genealogical language, such as ‘parent-child’, ‘uncle-nephew’, ‘first cousins’, etc.
            </div>
            <div style={{paddingBottom: 20, maxWidth: 1000}}>
                Whole genome sequencing was performed on each animal and genotypes were called with GATK haplotype caller. Principal components analysis was performed with GCTA (https://yanglab.westlake.edu.cn/software/gcta/#PCA) and kinship coefficients were calculated with KING (https://www.kingrelatedness.com/). Analyses were performed by Ric del Rosario (Broad Institute).
            </div>
            <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
                <Tabs value={value} onChange={handleChange} aria-label="basic tabs example">
                    <Tab label="Population Genetic Diversity" {...a11yProps(0)} />
                    <Tab label="Kinship" {...a11yProps(1)} />
                    <Tab label="Genetic Variants" {...a11yProps(2)} hidden={jbrowseId == null}/>
                </Tabs>
            </Box>
            <div className="row">
                <div className="col-md-6">
                    <div className="panel panel-default">
                        <div className="panel-body">
                            {value === 0 && <ScatterChart data={pcaData}/>}
                            {value === 1 && <KinshipTable data={kinshipData}/>}
                            {value === 2 && <GenomeBrowser jbrowseId={jbrowseId}/>}
                        </div>
                    </div>
                </div>
            </div>
        </ErrorBoundary>
        </>
    );
}