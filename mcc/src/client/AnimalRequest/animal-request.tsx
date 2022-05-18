import React, { FormEvent, useState } from 'react';
import { ActionURL, getServerContext, Query } from '@labkey/api';
import {
    AnimalCohort,
    AnimalRequestModel,
    CoInvestigatorModel,
    queryRequestInformation
} from '../components/RequestUtils';

import Tooltip from './components/tooltip';
import Title from './components/title';
import Input from './components/input';
import Select from './components/select';
import CoInvestigators from './components/co-investigators';
import TextArea from './components/text-area';
import YesNoRadio from './components/yes-no-radio';
import AnimalBreeding from './components/animal-breeding';
import IACUCProtocol from './components/iacuc-protocol';
import Funding from './components/funding';
import AnimalCohorts from './components/animal-cohort';
import Button from './components/button';
import SavingOverlay from './saving-overlay';
import ErrorMessageHandler from './components/error-message-handler';

import {
    animalWellfarePlaceholder,
    certificationLabel,
    collaborationsPlaceholder,
    earlyInvestigatorTooltip,
    existingMarmosetColonyOptions,
    existingNHPFacilityOptions,
    experimentalRationalePlaceholder,
    institutionTypeOptions,
    methodsProposedPlaceholder,
    signingOfficialTooltip
} from './components/values';

export function AnimalRequest() {
    const requestId = (new URLSearchParams(window.location.search)).get("requestId")

    const [isSubmitting, setIsSubmitting] = useState(false)
    const [displayOverlay, setDisplayOverlay] = useState(false)
    const [requestData, setRequestData] = useState<AnimalRequestModel>(null)
    const [stateRollbackOnFailure, setStateRollbackOnFailure] = useState(requestData?.request?.status)

    const [ deletedCohortRecords, setDeletedCohortRecords ] = useState(new Set<number>())
    const [ deletedCoIRecords, setDeletedCoIRecords ] = useState(new Set<number>())

    function onAddCohort() {
        requestData.cohorts.push(new AnimalCohort())
        setRequestData({...requestData})
    }

    function onRemoveCohort(cohort: AnimalCohort) {
        // NOTE: dont allow removing all cohorts
        if (requestData.cohorts.length > 1) {
            requestData.cohorts = [...requestData.cohorts.filter(item => item.uuid !== cohort.uuid)]

            if (cohort.rowid) {
                deletedCohortRecords.add(cohort.rowid)
                setDeletedCohortRecords(new Set(deletedCohortRecords))
            }

            setRequestData({...requestData})
        }
    }

    function onAddInvestigator() {
        requestData.coinvestigators.push(new CoInvestigatorModel())
        setRequestData({...requestData})
    }

    function onRemoveCoInvestigator(coInvestigator: CoInvestigatorModel) {
        if (requestData.coinvestigators.length) {
            requestData.coinvestigators = [...requestData.coinvestigators.filter(item => item.uuid !== coInvestigator.uuid)]

            if (coInvestigator.rowid) {
                deletedCoIRecords.add(coInvestigator.rowid)
                setDeletedCoIRecords(new Set(deletedCoIRecords))
            }

            setRequestData({...requestData})
        }
    }

    if (!requestData) {
        queryRequestInformation(requestId, handleFailure).then((model) => {
            setRequestData(model)
            setStateRollbackOnFailure(requestData?.request.status)
        })
    }

    if (!requestData || !requestData?.dataLoaded) {
        return (
            <div className="tw-flex tw-justify-center tw-items-center">
                <div style={{ borderBottom: "2px solid #3495d2" }} className="tw-animate-spin tw-rounded-full tw-h-32 tw-w-32"></div>
            </div>
        )
    }
    else if (requestData.dataLoaded === true && !requestData.request) {
        return(<div>There is no request with id: {requestId}</div>)
    }

    function handleFailure(response) {
        console.error(response.exception)
        console.error(response)
        alert(response.exception)  //this is probably what you want to show. An example would be to submit data with a long value for middle initial (>14 characters)
        setDisplayOverlay(false)

        requestData.request.status = stateRollbackOnFailure
        setRequestData({...requestData})
    }

    function doEnforceRequiredFields() {
        return isSubmitting || requestData.request.status !== "Draft"
    }

    // The general idea is that users with MCCRequestAdminPermission can edit all states.
    // A normal user can only edit their own requests, and only when in draft form. Once submitted, they can no longer edit them.
    function hasEditPermission() {
        const ctx = getServerContext().getModuleContext('mcc') || {};
        if (!!ctx.hasRequestAdminPermission) {
            return true
        }

        if (!requestData.request.status) {
            return true
        }

        return "Draft" === requestData.request.status
    }

    function handleSubmitButton(e, isSubmitting) {
        setIsSubmitting(isSubmitting);

        if (!isSubmitting) {
            // Use this to reset each field's error state
            e.target.form.querySelectorAll('input, select, textarea').forEach(function(e){
                e.checkValidity()
            })
        }
    }

    function getSubmitButtonText() {
        switch (requestData.request.status) {
            case "Draft":
                return "Submit"
            default:
                return "Update Request"
        }
    } 

    function getSaveButtonText() {
        return "Save"
    }

    function getCoinvestigatorCommands(data) {
        let commands = []

        if (requestData.coinvestigators.length) {
            requestData.coinvestigators.forEach((rec, i) => {
                commands.push({
                    command: rec.rowid ? "update" : "insert",
                    schemaName: "mcc",
                    queryName: "coinvestigators",
                    rows: [{
                        "rowid": rec.rowid,
                        "requestId": requestData.request.objectid,
                        "lastname": data.get("coinvestigators-" + i + "-lastName"),
                        "firstname": data.get("coinvestigators-" + i + "-firstName"),
                        "middleinitial": data.get("coinvestigators-" + i + "-middleInitial"),
                        "institutionname": data.get("coinvestigators-" + i + "-institution"),
                    }]
                })
            })
        }

        if (deletedCoIRecords.size) {
            deletedCoIRecords.forEach(rowId => {
                commands.push({
                    command: "delete",
                    schemaName: "mcc",
                    queryName: "coinvestigators",
                    rows: [{
                        "rowid": rowId,
                    }]
                })
            })
        }

        return commands
    }

    function getAnimalCohortCommands(data) {
        let commands = []

        if (requestData.cohorts.length) {
            requestData.cohorts.forEach((rec, i) => {
                commands.push({
                    command: rec.rowid ? "update" : "insert",
                    schemaName: "mcc",
                    queryName: "requestcohorts",
                    rows: [{
                        "rowid": rec.rowid,
                        "requestId": requestData.request.objectid,
                        "numberofanimals": data.get("animal-cohorts-" + i + "-numberofanimals"),
                        "sex": data.get("animal-cohorts-" + i + "-sex"),
                        "othercharacteristics": data.get("animal-cohorts-" + i + "-othercharacteristics"),
                    }]
                })
            })
        }

        if (deletedCohortRecords.size) {
            deletedCohortRecords.forEach(rowId => {
                commands.push({
                    command: "delete",
                    schemaName: "mcc",
                    queryName: "requestcohorts",
                    rows: [{
                        "rowid": rowId,
                    }]
                })
            })
        }

        return commands
    }

    function handleSubmit(e: FormEvent) {
        e.preventDefault()
        setDisplayOverlay(true)

        // NOTE: the idea is that when the user hits 'submit', this changes Draft to Submitted.
        // Any other action preserved the status as-is
        if (isSubmitting) {
            setStateRollbackOnFailure(requestData.request.status)
            if (requestData.request.status === 'Draft') {
                requestData.request.status = 'Submitted'

                setRequestData({...requestData})
            }
        }
    
        const el = e.currentTarget as HTMLFormElement
        const data = new FormData(el)
        el.querySelectorAll<HTMLSelectElement>('select[multiple]').forEach(function(x){
            data.set(x.id, Array.from(x.selectedOptions, option => option.value).join(','))
        })

        if (!requestData.request.status) {
            console.error('Request being submitted without a status!')
        }

        if (!requestData.request.objectid) {
            console.error('Request being submitted without an objectId!')
        }

        // NOTE: use a proper v4 UUID so this is compatible with the sqlserver ENTITYID datatype
        let coinvestigatorCommands = getCoinvestigatorCommands(data)
        let cohortCommands = getAnimalCohortCommands(data)

        Query.saveRows({
            commands: [{
                    command: requestData.request.rowid ? "update" : "insert",
                    schemaName: "mcc",
                    queryName: "animalrequests",
                    rows: [{
                        "rowid": requestData.request.rowid,
                        "objectId": requestData.request.objectid,
                        "lastname": data.get("investigator-last-name"),
                        "firstname": data.get("investigator-first-name"),
                        "middleinitial": data.get("investigator-middle-initial"),
                        "earlystageinvestigator": data.get("is-early-stage-investigator"),
                        "institutionname": data.get("institution-name"),
                        "institutioncity": data.get("institution-city"),
                        "institutionstate": data.get("institution-state"),
                        "institutioncountry": data.get("institution-country"),
                        "institutiontype": data.get("institution-type"),
                        "officiallastname": data.get("official-last-name"),
                        "officialfirstname": data.get("official-first-name"),
                        "officialemail": data.get("official-email"),
                        "fundingsource": data.get("funding-source"),
                        "experimentalrationale": data.get("experiment-rationale"),
                        "methodsproposed": data.get("methods-proposed"),
                        "collaborations": data.get("collaborations"),
                        "isbreedinganimals": data.get("animal-breeding-is-planning-to-breed-animals"),
                        "breedingpurpose": data.get("animal-breeding-purpose"),
                        "existingmarmosetcolony": data.get("existing-marmoset-colony"),
                        "existingnhpfacilities": data.get("existing-nhp-facilities"),
                        "animalwelfare": data.get("animal-welfare"),
                        "certify": !!data.get("certify"),
                        "vetlastname": data.get("vet-last-name"),
                        "vetfirstname": data.get("vet-first-name"),
                        "vetemail": data.get("vet-email"),
                        "iacucapproval": data.get("iacuc-approval"),
                        "iacucprotocol": data.get("iacuc-protocol"),
                        "grantnumber" : data.get("funding-grant-number"),
                        "applicationduedate": data.get("funding-application-due-date"),
                        "status": requestData.request.status,
                    }]
                },
                ...coinvestigatorCommands,
                ...cohortCommands
            ],
            success: function(response) {
                // set RowIDs:
                if (response.result[0].queryName !== 'animalrequests') {
                    console.error('The first command was not animal requests! This is not expected.')
                }

                const rowId = response.result[0].rows[0].rowid
                if (!rowId) {
                    console.error('No rowId found for the animalrequest row after save. This is not expected.')
                }
                else {
                    requestData.request.rowid = rowId
                }

                if (response.result[0].rows[0].rowid.status) {
                    requestData.request.status = response.result[0].rows[0].rowid.status
                }
                else {
                    console.error('Status was null for the animalrequest row after save. This is not expected.')
                }

                response.result.filter(x => x.command !== 'delete' && x.queryName === 'requestcohorts').forEach((x, idx) => {
                    if (requestData.cohorts.length < idx) {
                        console.error('There are more cohort saveRows commands than client-side records')
                        return
                    }

                    if (x.rows.length != 1) {
                        console.error('Expected a single row per cohort saveRows command, found: ' + x.rows.length)
                        return
                    }

                    if (requestData.cohorts[idx].rowid && requestData.cohorts[idx].rowid != x.rows[0].rowid) {
                        console.error('Cohort row has existing rowId but doesnt match the server: ' + requestData.cohorts[idx].rowid + ' / ' + x.rows[0].rowid)
                        return
                    }

                    requestData.cohorts[idx].rowid = x.rows[0].rowid
                })

                response.result.filter(x => x.command !== 'delete' && x.queryName === 'coinvestigators').forEach((x, idx) => {
                    if (requestData.coinvestigators.length < idx) {
                        console.error('There are more coinvestigators saveRows commands than client-side records')
                        return
                    }

                    if (x.rows.length != 1) {
                        console.error('Expected a single row per coinvestigators saveRows command, found: ' + x.rows.length)
                        return
                    }

                    if (requestData.coinvestigators[idx].rowid && requestData.coinvestigators[idx].rowid != x.rows[0].rowid) {
                        console.error('Coinvestigators row has existing rowId but doesnt match the server: ' + requestData.coinvestigators[idx].rowid + ' / ' + x.rows[0].rowid)
                        return
                    }

                    requestData.coinvestigators[idx].rowid = x.rows[0].rowid
                })

                response.result.filter(x => x.command === 'delete').forEach((x, idx) => {
                    if (x.rows.length != 1) {
                        console.error('Expected a single row per saveRows delete command, found: ' + x.rows.length)
                        return
                    }

                    const rowId = x.rows[0].rowid
                    if (x.queryName === 'requestcohorts') {
                        deletedCohortRecords.delete(rowId)
                        setDeletedCohortRecords(new Set(deletedCohortRecords))
                    }
                    else if (x.queryName === 'coinvestigators') {
                        deletedCoIRecords.delete(rowId)
                        setDeletedCoIRecords(new Set(deletedCoIRecords))
                    }
                })

                if (deletedCoIRecords.size) {
                    console.log('there are still records in deletedCoIRecords: ' + deletedCoIRecords.size)
                }

                if (deletedCohortRecords.size) {
                    console.log('there are still records in deletedCohortRecords: ' + deletedCohortRecords.size)
                }

                setRequestData({...requestData})
                setStateRollbackOnFailure(requestData.request.status)

                if (!requestData.request.rowid) {
                    console.error("Missing request rowid after save")
                }

                requestData.cohorts.forEach(x => {
                    if (!x.rowid) {
                        console.error("Missing cohort rowid after save")
                    }
                })

                requestData.coinvestigators.forEach(x => {
                    if (!x.rowid) {
                        console.error("Missing coinvestigator rowid after save")
                    }
                })

                setDisplayOverlay(false)
                if (isSubmitting) {
                    const returnURL = (new URLSearchParams(window.location.search)).get("requestId")
                    let dest = ActionURL.buildURL('mcc', 'mccRequests.view')
                    if (returnURL) {
                        try {
                            dest = new URL(returnURL).href
                        }
                        catch (e) {

                        }
                    }

                    window.location.href = dest
                }
            },
            failure: handleFailure
        })
    }

    return (
        <>
        <form className="tw-w-full tw-max-w-4xl" onSubmit={handleSubmit} autoComplete="off">
            <h3>General Information</h3>
            <ErrorMessageHandler isSubmitting={isSubmitting}>
            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-4">
                <Title text="1. Principal Investigator*"/>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="investigator-last-name" ariaLabel="Last Name" isSubmitting={isSubmitting} required={doEnforceRequiredFields()} placeholder="Last Name" defaultValue={requestData.request.lastname}/>
                </div>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="investigator-first-name" ariaLabel="First Name" isSubmitting={isSubmitting} required={doEnforceRequiredFields()} placeholder="First Name" defaultValue={requestData.request.firstname}/>
                </div>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="investigator-middle-initial" isSubmitting={isSubmitting} required={false} placeholder="Middle Initial" defaultValue={requestData.request.middleinitial} maxLength="8"/>
                </div>
            </div>
            </ErrorMessageHandler>

            <ErrorMessageHandler isSubmitting={isSubmitting}>
            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-4">
                <div className="tw-relative tw-w-full tw-mb-6 md:tw-mb-0">
                    <Title text="2. Are you an early-stage investigator?&nbsp;"/>
                    <Tooltip id="early-stage-investigator-helper"
                       text={earlyInvestigatorTooltip}
                    />
                </div>

                <div className="tw-w-full tw-px-3 tw-mt-6">
                    <YesNoRadio id="is-early-stage-investigator" ariaLabel="Early Stage Investigator" isSubmitting={isSubmitting} required={doEnforceRequiredFields()} defaultValue={requestData.request.earlystageinvestigator}/>
                </div>
            </div>
            </ErrorMessageHandler>

            <ErrorMessageHandler isSubmitting={isSubmitting}>
            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-4">
                <Title text="3. Affiliated research institution*"/>

                <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="institution-name" ariaLabel="Institution Name" isSubmitting={isSubmitting} placeholder="Name" required={doEnforceRequiredFields()} defaultValue={requestData.request.institutionname}/>
                </div>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="institution-city" ariaLabel="Institution City" isSubmitting={isSubmitting} placeholder="City" required={doEnforceRequiredFields()} defaultValue={requestData.request.institutioncity}/>
                </div>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="institution-state" ariaLabel="Institution State" isSubmitting={isSubmitting} placeholder="State" required={doEnforceRequiredFields()} defaultValue={requestData.request.institutionstate}/>
                </div>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="institution-country" ariaLabel="Institution Country" isSubmitting={isSubmitting} placeholder="Country" required={doEnforceRequiredFields()} defaultValue={requestData.request.institutioncountry}/>
                </div>

                <Title text="4. Affiliated Research Institution Type*"/>

                <div className="tw-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Select id="institution-type" ariaLabel="Institution Type" isSubmitting={isSubmitting} placeholder="Type" required={doEnforceRequiredFields()} defaultValue={requestData.request.institutiontype} options={institutionTypeOptions}/>
                </div>
            </div>
            </ErrorMessageHandler>

            <ErrorMessageHandler isSubmitting={isSubmitting}>
            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-4">
                <div className="tw-relative tw-w-full tw-mb-6 md:tw-mb-0">
                    <Title text="5. Institution Signing Official*&nbsp;"/>
                    <Tooltip id="signing-official-helper"
                         text={signingOfficialTooltip}
                    />
                </div>


                <div className="tw-flex tw-flex-wrap tw-mt-6">
                    <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="official-last-name" ariaLabel="Last Name" isSubmitting={isSubmitting} placeholder="Last Name" required={doEnforceRequiredFields()} defaultValue={requestData.request.officiallastname}/>
                    </div>

                    <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="official-first-name" ariaLabel="First Name" isSubmitting={isSubmitting} placeholder="First Name" required={doEnforceRequiredFields()} defaultValue={requestData.request.officialfirstname}/>
                    </div>

                    <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="official-email" ariaLabel="Email Address" isSubmitting={isSubmitting} placeholder="Email Address" required={doEnforceRequiredFields()} defaultValue={requestData.request.officialemail}/>
                    </div>
                </div>
            </div>
            </ErrorMessageHandler>

            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                <Title text="6. Co-Investigators"/>

                <CoInvestigators isSubmitting={isSubmitting} required={doEnforceRequiredFields()} coinvestigators={requestData.coinvestigators} onAddRecord={onAddInvestigator} onRemoveRecord={onRemoveCoInvestigator} />
            </div>

            <Title text="7. Existing or proposed funding source (select all that apply)"/>
            {/* TODO: Make into checkbox group*/}
            <Funding id="funding" isSubmitting={isSubmitting} defaultValue={requestData.request} required={doEnforceRequiredFields()}/>

            <h3>Institutional Animal Facilities and Capabilities</h3>
            <div className="tw-w-full tw-px-3">
                <Title text="1. Does your institution have existing NHP facilities?"/>
                <ErrorMessageHandler isSubmitting={isSubmitting}>
                    <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-4">
                        <Select id="existing-nhp-facilities" ariaLabel="Existing NHP Facilities" isSubmitting={isSubmitting} options={existingNHPFacilityOptions} defaultValue={requestData.request.existingnhpfacilities} required={doEnforceRequiredFields()}/>
                    </div>
                </ErrorMessageHandler>

                <Title text="2. Does your institution have an existing marmoset colony?"/>
                <ErrorMessageHandler isSubmitting={isSubmitting}>
                    <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-4">
                        <Select id="existing-marmoset-colony" ariaLabel="Existing Marmoset Colony" isSubmitting={isSubmitting} options={existingMarmosetColonyOptions} defaultValue={requestData.request.existingmarmosetcolony} required={doEnforceRequiredFields()}/>
                    </div>
                </ErrorMessageHandler>

                <Title text="3. Do you plan to breed marmosets?"/>
                <div className="tw-w-full tw-px-3 tw-mb-4">
                    <AnimalBreeding id="animal-breeding" isSubmitting={isSubmitting} request={requestData.request} required={doEnforceRequiredFields()}/>
                </div>
            </div>

            <h3>Research Details</h3>

            <div className="tw-flex tw-flex-wrap tw-mx-2">
                <div className="tw-w-full tw-px-3 tw-mb-4">
                    <Title text={"1. " + experimentalRationalePlaceholder}/>
                    <Tooltip id="research-use-statement-helper"
                       text={experimentalRationalePlaceholder}
                    />

                    <ErrorMessageHandler isSubmitting={isSubmitting}>
                    <TextArea id="experiment-rationale" ariaLabel="Experimental rationale" isSubmitting={isSubmitting} placeholder={experimentalRationalePlaceholder} required={doEnforceRequiredFields()} defaultValue={requestData.request.experimentalrationale}/>
                    </ErrorMessageHandler>
                </div>

                <Title text="2. Animal Cohorts"/>
                <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-6">
                    <AnimalCohorts isSubmitting={isSubmitting} cohorts={requestData.cohorts} required={doEnforceRequiredFields()} onAddCohort={onAddCohort} onRemoveCohort={onRemoveCohort}/>
                </div>

                <Title text={"3. " + methodsProposedPlaceholder}/>
                <div className="tw-w-full tw-px-3 tw-mb-6">
                    <ErrorMessageHandler isSubmitting={isSubmitting}>
                    <div className="tw-w-full tw-px-3 tw-mb-6">
                        <TextArea id="methods-proposed" ariaLabel="Methods Proposed" isSubmitting={isSubmitting} placeholder={methodsProposedPlaceholder} required={doEnforceRequiredFields()} defaultValue={requestData.request.methodsproposed}/>
                    </div>
                    </ErrorMessageHandler>
                </div>

                <Title text={"4. " + collaborationsPlaceholder}/>
                <div className="tw-w-full tw-px-3 tw-mb-6">
                    <ErrorMessageHandler isSubmitting={isSubmitting}>
                    <div className="tw-w-full tw-px-3 tw-mb-6">
                        <TextArea id="collaborations" ariaLabel="Collaborations" isSubmitting={isSubmitting} placeholder={collaborationsPlaceholder} required={doEnforceRequiredFields()} defaultValue={requestData.request.collaborations}/>
                    </div>
                    </ErrorMessageHandler>
                </div>

                <Title text={"5. " + animalWellfarePlaceholder}/>
                <div className="tw-w-full tw-px-3 tw-mb-6">
                    <ErrorMessageHandler isSubmitting={isSubmitting}>
                    <div className="tw-w-full tw-px-3 tw-mb-6">
                        <TextArea id="animal-welfare" ariaLabel="Animal Welfare" isSubmitting={isSubmitting} placeholder={animalWellfarePlaceholder} required={doEnforceRequiredFields()} defaultValue={requestData.request.animalwelfare}/>
                    </div>
                    </ErrorMessageHandler>

                    <ErrorMessageHandler isSubmitting={isSubmitting}>
                    <div className="tw-w-full tw-px-3 tw-mb-6">
                        <input type="checkbox" name="certify" id="certify" aria-label="Certify" className={(isSubmitting ? "custom-invalid" : "")} required={doEnforceRequiredFields()} defaultChecked={requestData.request.certify}/>
                        <label className="tw-text-gray-700 ml-1">{certificationLabel}</label>
                    </div>
                    </ErrorMessageHandler>
                </div>

                <ErrorMessageHandler isSubmitting={isSubmitting}>
                <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-4">
                    <Title text="6. Attending veterinarian"/>

                    <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="vet-last-name" ariaLabel="Last Name" isSubmitting={isSubmitting} placeholder="Last Name" required={doEnforceRequiredFields()} defaultValue={requestData.request.vetlastname}/>
                    </div>

                    <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="vet-first-name" ariaLabel="First Name" isSubmitting={isSubmitting} placeholder="First Name" required={doEnforceRequiredFields()} defaultValue={requestData.request.vetfirstname}/>
                    </div>

                    <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="vet-email" ariaLabel="Email" isSubmitting={isSubmitting} placeholder="Email Address" required={doEnforceRequiredFields()} defaultValue={requestData.request.vetemail}/>
                    </div>
                </div>
                </ErrorMessageHandler>
            </div>

            <IACUCProtocol id="iacuc" isSubmitting={isSubmitting} required={doEnforceRequiredFields()} request={requestData.request}/>

            <div className="tw-flex tw-flex-wrap tw-mx-2">
                <Title text="Request Status: "/>{requestData.request.status}
            </div>

            <div className="tw-flex tw-flex-wrap tw-mx-2">
                <button className="tw-ml-auto tw-bg-red-500 hover:tw-bg-red-400 tw-text-white tw-font-bold tw-py-4 tw-mt-2 tw-px-6 tw-border-none tw-rounded" onClick={(e) => {
                    e.preventDefault()

                    if (confirm("You are about to leave this page.")) {
                        window.location.href = ActionURL.buildURL('mcc', 'mccRequests.view');
                    }
                }}>Cancel</button>

                <Button onClick={(e) => {
                    handleSubmitButton(e, false);
                 }} text={getSaveButtonText()} display={hasEditPermission()}/>

                <Button onClick={(e) => {
                    handleSubmitButton(e, true);
                 }} text={getSubmitButtonText()} display={hasEditPermission()}/>
            </div>
        </form>

        <SavingOverlay display={displayOverlay} />
        </>
     )
}
