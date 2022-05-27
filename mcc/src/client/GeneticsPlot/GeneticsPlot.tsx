import React, { useEffect, useState } from 'react';
import { getServerContext } from '@labkey/api';
import { TSV } from 'tsv';

import ScatterChart from './ScatterChart';
import { ErrorBoundary } from '@labkey/components';

export function GeneticsPlot() {
    const [pcaData, setPcaData] = useState(null);
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
    }, [] /* only run the effect on mount */);

    if (!containerPath) {
        return (
            <div className="loading">
                <div>Error: must set the MCCContainer module property</div>
            </div>
        );
    }

    if (pcaData === null) {
        return (
            <div className="loading">
                <div>Loading...</div>
            </div>
        );
    }

    return (
        <>
            <ErrorBoundary>
            <div className="row">
                <div className="col-md-4">
                    <div className="panel panel-default">
                        <div className="panel-heading">Census</div>
                        <div className="panel-body">
                            <ScatterChart data={pcaData}/>
                        </div>
                    </div>
                </div>
            </div>
            </ErrorBoundary>
        </>
    );
}