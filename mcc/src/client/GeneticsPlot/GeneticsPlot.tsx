import React, { useEffect, useState } from 'react';
import { ActionURL, Filter, getServerContext, Query } from '@labkey/api';
import '../components/dashboard/dashboard.css';
import ScatterChart from './ScatterChart';
import { Box, Tab, Tabs } from '@mui/material';
import KinshipTable from './KinshipTable';
import { ErrorBoundary } from '../components/ErrorBoundary';
import SequenceDataTable from './SequenceDataTable';


export function GeneticsPlot() {
    const [pcaData, setPcaData] = useState([]);
    const [kinshipData, setKinshipData] = useState([]);
    const [sequenceData, setSequenceData] = useState([]);
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

        Query.selectRows({
            containerPath: containerPath,
            schemaName: 'study',
            queryName: 'genomicDatasets',
            columns: 'Id,datatype,sra_accession,total_reads,objectid',
            success: function(results) {
                setSequenceData(results.rows.map((row) => {
                    return({
                        id: row.objectid,
                        Id: row.Id,
                        datatype: row.datatype,
                        sra_accession: row.sra_accession,
                        total_reads: row.total_reads
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
                Over the past few years, the MCC team has been working on extracting, sequencing and analyzing DNA from
                marmosets across the participating breeding centers. While we have deposited the raw sequence data for
                578 marmosets on NCBI's Sequence Read Archive (SRA), we are excited to report that the MCC portal now
                houses a call set with single nucleotide variants and short indels for over 800 individuals. The genetic analyses
                described here were performed by Karina Ray (ONPRC), Murillo Rodrigues (ONPRC), and
                Ric del Rosario (Broad Institute). Please contact us at <a href="mailto:mcc@ohsu.edu">mcc@ohsu.edu</a> with any
                questions.
                <p/>
                { jbrowseId ? (
                    <>
                        In addition to the information in the tabs below, you can use the MCC genome browser to view variants and/or search by gene:
                        <p/>
                        <ul>
                            <li>
                                <a style={{fontWeight: 'bold'}} href={ActionURL.buildURL('jbrowse', 'jbrowse', null, {session: jbrowseId})}>Click here to open the genome browser</a>
                            </li>
                        </ul>
                    </>
                ) : null }
            </div>

            <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
                <Tabs value={value} onChange={handleChange} aria-label="basic tabs example">
                    <Tab label="Population Genetic Diversity" {...a11yProps(0)} />
                    <Tab label="Kinship" {...a11yProps(1)} />
                    <Tab label="Sequence Datasets" {...a11yProps(2)}/>
                </Tabs>
            </Box>
            <div className="row">
                <div className="col-md-6">
                    <div className="panel panel-default">
                        <div className="panel-body">
                            {value === 0 && <ScatterChart data={pcaData}/>}
                            {value === 1 && <KinshipTable data={kinshipData}/>}
                            {value === 2 && <SequenceDataTable data={sequenceData}/>}
                        </div>
                    </div>
                </div>
            </div>
        </ErrorBoundary>
        </>
    );
}