import './dashboard.css';

import React, { useState, useEffect } from 'react';
import { Query } from '@labkey/api';

import PieChart from './PieChart';
import BarChart from './BarChart';

export function Dashboard() {
    const [demographics, setDemographics] = useState(null);

    useEffect(() => {
        Query.selectRows({
                schemaName: 'study',
                queryName: 'demographics',
                columns: 'Id,birth,death,gender,species,colony,status,Id/age/AgeFriendly,Id/ageClass/label',
                success: function(results) {
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
                            <PieChart fieldName = "gender" demographics={demographics} />
                        </div>
                    </div>
                </div>
            </div>
            <div className="row">
                <div className="col-md-6">
                    <div className="panel panel-default">
                        <div className="panel-heading">Age</div>
                        <div className="panel-body">
                            <PieChart fieldName = "Id/ageClass/label" demographics={demographics} />
                        </div>
                    </div>
                </div>
                <div className="col-md-6">
                    <div className="panel panel-default">
                        <div className="panel-heading">Center</div>
                        <div className="panel-body">
                            <BarChart fieldName = "colony" demographics={demographics} />
                        </div>
                    </div>
                </div>
            </div>
        </>
    );
}