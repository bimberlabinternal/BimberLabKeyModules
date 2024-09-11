import '../components/dashboard/dashboard.css';

import React, { useEffect, useState } from 'react';
import { ActionURL, Filter, getServerContext, Query } from '@labkey/api';

import PieChart from '../components/dashboard/PieChart';
import BarChart from '../components/dashboard/BarChart';
import { ActiveElement, Chart, ChartEvent } from 'chart.js/dist/types/index';

export function Dashboard() {
    const [demographics, setDemographics] = useState(null);
    const [living, setLiving] = useState(null);
    const [u24Assigned, setu24Assigned] = useState(null);
    const [availableForTransfer, setAvailableForTransfer] = useState(null);
    const [requestRows, setRequestRows] = useState(null);
    const [censusRows, setCensusRows] = useState(null);
    const [birthData, setBirthData ] = useState(null);
    const [breedingPairData, setBreedingPairData ] = useState(null);

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
            columns: 'requestId/status',
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
            columns: 'yearNo,startdate,enddate,centerName,totalLiving,totalLivingU24,totalBreedingPairs,totalBreedingPairsU24,totalOffspring,totalOffspringU24,marmosetsShipped',
            success: function(results) {
                if (isApiSubscribed) {
                    setCensusRows(results.rows);

                    setBreedingPairData(results.rows.flatMap(row => {
                        return Array(row.totalBreedingPairs).fill({
                            yearNo: row.yearNo,
                            centerName: row.centerName
                        })
                    }))

                    setBirthData(results.rows.flatMap(row => {
                        return Array(row.totalOffspring).fill({
                            yearNo: row.yearNo,
                            centerName: row.centerName
                        })
                    }))
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

    const clickHandler = function(event: ChartEvent, elements: ActiveElement[], chart: Chart){
        console.log(event)
        console.log(elements)
        console.log(chart)
    }

    return (
        <>
            <div className="row">
                <div className="col-md-4">
                    <div className="panel panel-default">
                        <div className="panel-heading">U24 Census</div>
                        <div className="row">
                            <div className="panel-body count-panel-body">
                                <div className="count-panel-text"><a href={ActionURL.buildURL("project", "begin", "", {pageId: "animalData"})}>{u24Assigned.length}</a></div>
                                <div className="small text-muted text-center">Total U24 Animals</div>
                            </div>
                        </div>
                        <div className="row mcc-col-centered">
                            <div className="col-md-3">
                                <div className="panel-body count-panel-body">
                                    <div className="count-panel-text-small"><a href={ActionURL.buildURL("project", "begin", "", {pageId: "animalData", "u24.Availability~eq": "available for transfer"})}>{availableForTransfer.length}</a></div>
                                    <div className="small text-muted text-center">Available</div>
                                </div>
                            </div>
                            <div className="col-md-3">
                                <div className="panel-body count-panel-body">
                                    <div className="count-panel-text-small"><a href={ActionURL.buildURL("project", "begin", "", {pageId: "requests"})}>{requestRows.length}</a></div>
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
                        <div className="panel-heading">Request Summary</div>
                        <div className="panel-body">
                            <PieChart fieldName = "requestId/status" demographics={requestRows} />
                        </div>
                    </div>
                </div>
                <div className="col-md-4">
                    <div className="panel panel-default">
                        <div className="panel-heading">Age (Living Animals)</div>
                        <div className="panel-body">
                            <BarChart demographics={living} fieldName="Id/ageClass/label" groupField="gender/meaning" onClick={clickHandler} />
                        </div>
                    </div>
                </div>
            </div>
            <div className="row">
                <div className="col-md-4">
                    <div className="panel panel-default">
                        <div className="panel-heading">U24 Births By Year</div>
                        <div className="panel-body">
                            <BarChart demographics={birthData} fieldName="centerName" groupField="yearNo" indexAxis="x"/>
                        </div>
                    </div>
                </div>
                <div className="col-md-4">
                    <div className="panel panel-default">
                        <div className="panel-heading">U24 Breeding Pairs</div>
                        <div className="panel-body">
                            <BarChart demographics={breedingPairData} fieldName="centerName" groupField="yearNo" indexAxis="x" />
                        </div>
                    </div>
                </div>
            </div>
        </>
    );
}