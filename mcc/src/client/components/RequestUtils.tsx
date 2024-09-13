import React from 'react';
import { Filter, getServerContext, Query } from '@labkey/api';
import { v4 as uuidv4 } from 'uuid';

export class AnimalRequestModel {
    request: AnimalRequestProps = new AnimalRequestProps();
    coinvestigators: CoInvestigatorModel[] = [];
    cohorts: AnimalCohort[] = [];
    shipments: ShipmentModel[] = [];
    dataLoaded: boolean = false;
}

export class AnimalRequestProps {
    status: string = 'Draft';
    narrative: string;
    title: string;
    neuroscience: string;
    diseasefocus: string;
    census: boolean;
    censusreason: string;
    middleinitial: string;
    lastname: string;
    firstname: string;
    earlystageinvestigator: boolean;
    institutionname: string;
    institutioncity: string;
    institutionstate: string;
    institutioncountry: string;
    institutiontype: string;
    officiallastname: string;
    officialfirstname: string;
    officialemail: string;
    fundingsource: string;
    grantnumber: string;
    experimentalrationale: string;
    breedinganimals: string;
    breedingpurpose: string;
    terminalprocedures: boolean;
    methodsproposed: string;
    collaborations: string;
    iacucprotocol: string;
    iacucapproval: string;
    existingmarmosetcolony: string;
    existingnhpfacilities: string;
    rowid: number;
    certify: boolean;
    animalwelfare: string;
    vetlastname: string;
    vetemail: string;
    vetfirstname: string;
    objectid: string;
    comments: string;
}

export class ShipmentModel {
    Id: string;
    date: string;
    source: string;
    objectid: string;
}

export class CoInvestigatorModel {
    rowid: number;
    requestId: string;
    lastname: string;
    firstname: string;
    middleinitial: string;
    institutionname: string;
    uuid: string = uuidv4().toUpperCase();
}

export class AnimalCohort {
    rowid: number;
    requestId: string;
    numberofanimals: number;
    sex: string;
    othercharacteristics: string;
    uuid: string = uuidv4().toUpperCase();
}

function canReadMccStudy() {
    const ctx = getServerContext().getModuleContext('mcc') || {};

    return !!ctx.hasAnimalDataReadPermission  && !!ctx.hasMccStudyReadPermission
}

function getMccDataContainerPath() {
    const ctx = getServerContext().getModuleContext('mcc') || {};

    return ctx.hasAnimalDataReadPermission ? ctx.MCCContainer : null
}

export async function queryRequestInformation(requestId, handleFailure) {
    const requestData = new AnimalRequestModel()

    if (!requestId) {
        requestData.dataLoaded = true
        requestData.request.objectid = requestData.request.objectid || uuidv4().toUpperCase()
        requestData.request.status = 'Draft'
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
                "narrative",
                "title",
                "neuroscience",
                "diseasefocus",
                "census",
                "censusreason",
                "terminalprocedures",
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
                "breedinganimals",
                "breedingpurpose",
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
                "comments",
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
                if (resp.rows.length) {
                    // NOTE: this property is purely for client-side UI and not persisted in the DB, so create a value
                    resp.rows.map(c => c.uuid = uuidv4().toUpperCase())
                }

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

                    // NOTE: this property is purely for client-side UI and not persisted in the DB, so create a value
                    requestData.cohorts.map(c => c.uuid = uuidv4().toUpperCase())
                }

                resolve(requestData.cohorts)
            },
            failure: handleFailure
        })
    })]

    if (canReadMccStudy()) {
      promises.push(new Promise<any>((resolve, reject) => {
          Query.selectRows({
              containerPath: getMccDataContainerPath(),
              schemaName: "study",
              queryName: "departure",
              columns: [
                  "Id",
                  "date",
                  "source",
                  "objectid"
              ],
              filterArray: [
                  Filter.create('mccRequestId/objectId', requestId)
              ],
              success: function (resp) {
                  // NOTE: abort so that we preserve the default value of one empty row
                  if (!resp.rows.length) {
                      requestData.shipments = []
                  } else {
                      requestData.shipments = resp.rows
                  }

                  resolve(requestData.shipments)
              },
              failure: handleFailure
          })
      }))
    }

    return await Promise.all(promises).then(values => {
        requestData.request = values[0]
        requestData.coinvestigators = values[1]
        requestData.cohorts = values[2]
        if (promises.length > 3) {
            requestData.shipments = values[3]
        }
        requestData.dataLoaded = true

        return(requestData)
    })
}
