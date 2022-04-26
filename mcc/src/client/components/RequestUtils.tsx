import React from 'react';
import { Filter, Query } from '@labkey/api';
import { nanoid } from 'nanoid';

export class AnimalRequestModel {
    request: AnimalRequestProps = new AnimalRequestProps();
    coinvestigators: CoInvestigatorModel[] = [];
    cohorts: AnimalCohort[] = [];
    dataLoaded: boolean = false;
}

export class AnimalRequestProps {
    status: string = 'Draft';
    middleinitial: string;
    lastname: string;
    firstname: string;
    earlystageinvestigator: string;
    institutionname: string;
    institutioncity: string;
    institutionstate: string;
    institutioncountry: string;
    institutiontype: string;
    officiallastname: string;
    officialfirstname: string;
    officialemail: string;
    experimentalrationale: string;
    isbreedinganimals: boolean;
    breedingpurpose: string;
    methodsproposed: string;
    collaborations: string;
    researcharea: string;
    otherjustification: string;
    iacucprotocol: string;
    existingmarmosetcolony: string;
    existingnhpfacilities: string;
    rowid: number;
    certify: boolean;
    animalwelfare: string;
    vetlastname: string;
    vetemail: string;
    vetfirstname: string;
    objectid: string;
}

export class CoInvestigatorModel {
    rowid: number;
    requestId: string;
    lastname: string;
    firstname: string;
    middleinitial: string;
    institutionname: string;
    uuid: string = nanoid();
}

export class AnimalCohort {
    rowid: number;
    requestId: string;
    numberofanimals: number;
    sex: string;
    othercharacteristics: string;
    uuid: string = nanoid();
}

export async function queryRequestInformation(requestId, handleFailure) {
    const requestData = new AnimalRequestModel()

    if (!requestId) {
        requestData.dataLoaded = true
        requestData.cohorts = [new AnimalCohort()]
        return(requestData)
    }

    const promises = [new Promise<any>((resolve, reject) => {
        Query.selectRows({
            schemaName: "mcc",
            queryName: "animalrequests",
            columns: [
                "rowid",
                "objectId",
                "lastname",
                "firstname",
                "middleinitial",
                "earlystageinvestigator",
                "institutionname",
                "institutioncity",
                "institutionstate",
                "institutioncountry",
                "institutiontype",
                "officiallastname",
                "officialfirstname",
                "officialemail",
                "fundingsource",
                "experimentalrationale",
                "methodsproposed",
                "collaborations",
                "isbreedinganimals",
                "breedingpurpose",
                "researcharea",
                "otherjustification",
                "existingmarmosetcolony",
                "existingnhpfacilities",
                "animalwelfare",
                "certify",
                "vetlastname",
                "vetfirstname",
                "vetemail",
                "iacucapproval",
                "iacucprotocol",
                "grantnumber",
                "applicationduedate",
                "status"
            ],
            filterArray: [
                Filter.create('objectId', requestId)
            ],
            success: function (resp) {
                resolve(resp.rows[0])
            },
            failure: handleFailure
        })
    }),
    new Promise<any>((resolve, reject) => {
        Query.selectRows({
            schemaName: "mcc",
            queryName: "coinvestigators",
            columns: [
                "rowid",
                "requestId",
                "lastname",
                "firstname",
                "middleinitial",
                "institutionname",
            ],
            filterArray: [
                Filter.create('requestId', requestId)
            ],
            success: function (resp) {
                resolve(resp.rows)
            },
            failure: handleFailure
        })
    }),
    new Promise<any>((resolve, reject) => {
        Query.selectRows({
            schemaName: "mcc",
            queryName: "requestcohorts",
            columns: [
                "rowid",
                "requestId",
                "numberofanimals",
                "sex",
                "othercharacteristics",
            ],
            filterArray: [
                Filter.create('requestId', requestId)
            ],
            success: function (resp) {
                // NOTE: abort so that we preserve the default value of one empty row
                if (!resp.rows.length) {
                    requestData.cohorts = [new AnimalCohort()]
                } else {
                    requestData.cohorts = resp.rows
                }

                resolve(requestData.cohorts)
            },
            failure: handleFailure
        })
    })]

    return await Promise.all(promises).then(values => {
        requestData.request = values[0]
        requestData.coinvestigators = values[1]
        requestData.cohorts = values[2]
        requestData.dataLoaded = true

        return(requestData)
    })
}
