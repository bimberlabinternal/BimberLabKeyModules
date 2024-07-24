import '../components/dashboard/dashboard.css';

import React, { useEffect, useState } from 'react';
import { Filter, getServerContext, Query } from '@labkey/api';

import PieChart from '../components/dashboard/PieChart';
import BarChart from '../components/dashboard/BarChart';

export function Dashboard() {
    const [demographics, setDemographics] = useState(null);
    const [living, setLiving] = useState(null);
    const [u24Assigned, setu24Assigned] = useState(null);
    const [availableForTransfer, setAvailableForTransfer] = useState(null);

    const ctx = getServerContext().getModuleContext('mcc') || {};
    const containerPath = ctx.MCCContainer || null;
    if (!containerPath) {
        return (
            <div className="loading">
                <div>Error: must set the MCCContainer module property</div>
            </div>
        );
    }

    let isApiSubscribed = true;
    useEffect(() => {
        Query.selectRows({
            containerPath: containerPath,
            schemaName: 'study',
            queryName: 'demographics',
            columns: 'Id,birth,death,gender/meaning,species,availability,colony,calculated_status,u24_status,Id/age/AgeFriendly,Id/ageClass/label',
            filterArray: [
                Filter.create('u24_status', true)
            ],
            success: function(results) {
                if (isApiSubscribed) {
                    setLiving(results.rows.filter(row => row.calculated_status === 'Alive' || row.calculated_status === 'alive'));
                    setu24Assigned(results.rows.filter(row => row.u24_status === true));
                    setAvailableForTransfer(results.rows.filter(row => row.availability === 'available for transfer'));
                    setDemographics(results.rows);
                }
            },
            failure: function(response) {
                if (isApiSubscribed) {
                    alert('There was an error loading data');
                    console.error(response);
                }
            },
            scope: this
        });

        return function cleanup() {
            isApiSubscribed = false
        }
    }, []);

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
                <div className="col-md-4">
                    <div className="panel panel-default">
                        <div className="panel-heading">Census</div>
                        <div className="row">
                            <div className="panel-body count-panel-body">
                                <div className="count-panel-text-small">{u24Assigned.length}</div>
                                <div className="small text-muted text-center">U24 Assigned</div>
                            </div>
                        </div>
                        <div className="row mcc-col-centered">
                            <div className="col-md-3">
                                <div className="panel-body count-panel-body">
                                    <div className="count-panel-text-small">{availableForTransfer.length}</div>
                                    <div className="small text-muted text-center">Available</div>
                                </div>
                            </div>
                            {/*<div className="col-md-3">*/}
                            {/*    <div className="panel-body count-panel-body">*/}
                            {/*    </div>*/}
                            {/*</div>*/}
                        </div>
                    </div>
                </div>
                <div className="col-md-4">
                    <div className="panel panel-default">
                        <div className="panel-heading">Center (All Animals)</div>
                        <div className="panel-body">
                            <PieChart fieldName = "colony" demographics={demographics} cutout = "30%" />
                        </div>
                    </div>
                </div>
            </div>
            <div className="row">
                <div className="col-md-4">
                    <div className="panel panel-default">
                        <div className="panel-heading">Age (Living Animals)</div>
                        <div className="panel-body">
                            <BarChart fieldName = "Id/ageClass/label" demographics={living} />
                        </div>
                    </div>
                </div>
                <div className="col-md-4">
                    <div className="panel panel-default">
                        <div className="panel-heading">Sex (Living Animals)</div>
                        <div className="panel-body">
                            <PieChart fieldName = "gender/meaning" demographics={living} />
                        </div>
                    </div>
                </div>
            </div>
        </>
    );
}