import './dashboard.css';

import React, { useState, useEffect } from 'react';
import { Query } from '@labkey/api';

import GenderChart from './GenderChart';

export function Dashboard() {
    const [demographics, setDemographics] = useState(null);

    useEffect(() => {
        Query.selectRows({
                schemaName: 'study',
                queryName: 'demographics',
                columns: 'Id,birth,death,gender,species,Id/age/AgeFriendly',
                success: function(results) {
                    console.log(results.rows);
                    setDemographics(results.rows);
                },
                failure: function(response) {
                    alert('It didnt work!');
                },
                scope: this
            });
    }, [] /* only run the effect on mount */);

    if (demographics === null) {
        return (
            <div className="loading">
                <div>Loading...</div>
            </div>
        );
    }

    return (
        <>
            <div className="row">
                <div className="col-md-6">
                    <div className="panel panel-default">
                        <div className="panel-heading">Count</div>
                        <div className="panel-body count-panel-body">
                            <div className="count-panel-text">{demographics.length}</div>
                            <div className="small text-muted">Marmosets tracked by MCC</div>
                        </div>
                    </div>
                </div>
                <div className="col-md-6">
                    <div className="panel panel-default">
                        <div className="panel-heading">Gender</div>
                        <div className="panel-body">
                            <GenderChart demographics={demographics} />
                        </div>
                    </div>
                </div>
            </div>
        </>
    );
}