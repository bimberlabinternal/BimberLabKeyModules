import React, { useEffect, useState } from 'react';
import { ActionURL, Filter, getServerContext, Query } from '@labkey/api';
import '../components/dashboard/dashboard.css';
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
                Over the past few years, the MCC team has been working on extracting, sequencing and analyzing DNA from
                marmosets across the participating breeding centers. While we have deposited the raw sequence data for
                578 marmosets on NCBI's Sequence Read Archive (SRA), we are excited to report that the MCC portal now
                houses a call set with single nucleotide variants and short indels for over 800 individuals.
                <p/>
                The MCC genomic database is extensive, with each individual being genotype at millions of variants
                across the genome. One way to summarize a large dataset can be done using Principal Component Analysis
                (PCA). PCA is a technique used across disciplines (from astronomy to genomics) that reduces the
                information in a multi-dimensional dataset to (fewer) principal components (PC) that retain overall
                trends and patterns in the original data. Biologically, this could mean merging together two variants
                that are always inherited together into just one PC, making the data easier to analyze while maintaining
                its most important patterns. See the **Visualization with PCA** tab below.
                <p/>
                Although PCA is useful for broad-scale comparisons, it is not very useful when trying to distinguish
                whether two individuals are siblings or first-cousins, for instance. For that, we have better statistics
                that can describe the genetic relatedness between two individuals. We estimated genetic relatedness for
                all pairs of individuals for which we have whole-genome data, and made these available under the
                **Kinship** tab. There you will find the inferred relationships between pairs of individuals as well as
                the calculated kinship coefficient, which is a quantitative measure of genetic relatedness
                (see <a href="https://en.wikipedia.org/wiki/Coefficient_of_relationship#Kinship_coefficient">here</a> for more details).
                <p/>
                It is possible to explore the full MCC database of variants with a graphical interface by accessing the
                **Genome Browser** tab. There you can, for example, visualize all the variants present in your gene of
                interest by typing it's name in the search bar.
                <p/>
                The genetic analyses described here were performed by Karina Ray (ONPRC), Murillo Rodrigues (ONPRC), and
                Ric del Rosario (Broad Institute). Please contact us at <a href="mailto:mcc@ohsu.edu">mcc@ohsu.edu</a> with any
                questions.
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