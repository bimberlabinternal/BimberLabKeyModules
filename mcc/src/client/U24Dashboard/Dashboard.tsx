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
    const [requestRows, setRequestRows] = useState(null);
    const [censusRows, setCensusRows] = useState(null);

    const ctx = getServerContext().getModuleContext('mcc') || {};
    const containerPath = ctx.MCCContainer || null;
    if (!containerPath) {
        return (
            <div className="loading">
                <div>Error: must set the MCCContainer module property</div>
            </div>
        );
    }

    const requestContainerPath = ctx.MCCRequestContainer || null;
    if (!requestContainerPath) {
        return (
            <div className="loading">
                <div>Error: must set the MCCRequestContainer module property</div>
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

        Query.selectRows({
            containerPath: requestContainerPath,
            schemaName: 'mcc',
            queryName: 'requestScores',
            success: function(results) {
                if (isApiSubscribed) {
                    setRequestRows(results.rows);
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

        Query.selectRows({
            containerPath: containerPath,
            schemaName: 'mcc',
            queryName: 'census',
            columns: 'yearNo,startdate,enddate,centerName,totalBreedingPairs,totalLivingOffspring,survivalRates,marmosetsShipped',
            success: function(results) {
                if (isApiSubscribed) {
                    setCensusRows(results.rows);
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

    if (demographics === null || requestRows == null || censusRows == null) {
        return (
            <div className="loading">
                <div>Loading...</div>
            </div>
        );
    }

    console.log(censusRows)
    console.log(requestRows)

    return (
        <>
            <div className="row">
                <div className="col-md-4">
                    <div className="panel panel-default">
                        <div className="panel-heading">U24 Census</div>
                        <div className="row">
                            <div className="panel-body count-panel-body">
                                <div className="count-panel-text">{u24Assigned.length}</div>
                                <div className="small text-muted text-center">Total U24 Animals</div>
                            </div>
                        </div>
                        <div className="row mcc-col-centered">
                            <div className="col-md-3">
                                <div className="panel-body count-panel-body">
                                    <div className="count-panel-text-small">{availableForTransfer.length}</div>
                                    <div className="small text-muted text-center">Available</div>
                                </div>
                            </div>
                            <div className="col-md-3">
                                <div className="panel-body count-panel-body">
                                    <div className="count-panel-text-small">{requestRows.length}</div>
                                    <div className="small text-muted text-center">Total Requests</div>
                                </div>
                            </div>
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
                            <BarChart demographics={living} fieldName="Id/ageClass/label" groupField="gender/meaning"  />
                        </div>
                    </div>
                </div>
                <div className="col-md-4">
                    <div className="panel panel-default">
                        <div className="panel-heading">Sex (Living Animals)</div>
                        <div className="panel-body">
                            {/*<PieChart fieldName = "gender/meaning" demographics={living} />*/}
                            PLACEHOLDER: Number of births over time, etc.
                        </div>
                    </div>
                </div>
            </div>
        </>
    );
}