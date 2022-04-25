import React, { FormEvent, useCallback, useEffect, useState } from 'react';
import { ActionURL, getServerContext, Query } from '@labkey/api';
import { nanoid } from 'nanoid';
import { AnimalRequestModel, queryRequestInformation } from '../components/RequestUtils';

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
import ResearchArea from './research-area';
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
    const [stateRollbackOnFailure, setStateRollbackOnFailure] = useState(requestData?.request.status)

    if (!requestData) {
        queryRequestInformation(requestId, handleFailure).then((model) => {
            setRequestData(model)
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
        return(<Title text="No such request."/>)
    }

    function handleFailure(response) {
        alert(response.exception)  //this is probably what you want to show. An example would be to submit data with a long value for middle initial (>14 characters)
        setDisplayOverlay(false)

        requestData.request.status = stateRollbackOnFailure
        setRequestData(requestData)
    }

    function getRequired() {
        switch (requestData.request.status) {
            case "Draft":
                return false
            default:
                return true
        }
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

    function handleSubmitButton(incrementStatus) {
        if (incrementStatus) {
            setIsSubmitting(true);
            setStateRollbackOnFailure(requestData.request.status)
        }

        setRequestData(requestData)
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

    function get_coinvestigator_commands(data, objectId) {
        let commands = []
        let i = 0

        if (requestId && requestData.coinvestigators.length > 0) {
            for (const coinvestigator of requestData.coinvestigators) {
                commands.push({
                    command: "delete",
                    schemaName: "mcc",
                    queryName: "coinvestigators",
                    rows: [{
                        "rowid": coinvestigator.rowid,
                    }]
                })
            }
        }

        while(data.get("coinvestigators-" + i + "-lastName") || data.get("coinvestigators-" + i + "-firstName") ||
              data.get("coinvestigators-" + i + "-middleInitial") || data.get("coinvestigators-" + i + "-institution")) {
            commands.push({
                command: "insert",
                schemaName: "mcc",
                queryName: "coinvestigators",
                rows: [{
                    "requestId": objectId,
                    "lastname": data.get("coinvestigators-" + i + "-lastName"),
                    "firstname": data.get("coinvestigators-" + i + "-firstName"),
                    "middleinitial": data.get("coinvestigators-" + i + "-middleInitial"),
                    "institutionname": data.get("coinvestigators-" + i + "-institution"),
                }]
            })

            i++
        }

        return commands
    }

    function get_animal_cohort_commands(data, objectId) {
        let commands = []
        let i = 0

        if (requestId && requestData.cohorts.length > 0) {
            for (const cohort of requestData.cohorts) {
                commands.push({
                    command: "delete",
                    schemaName: "mcc",
                    queryName: "requestcohorts",
                    rows: [{
                        "rowid": cohort.rowid,
                    }]
                })
            }
        }

        while(data.get("animal-cohorts-" + i + "-numberofanimals") || data.get("animal-cohorts-" + i + "-sex") ||
              data.get("animal-cohorts-" + i + "-othercharacteristics")) {
            commands.push({
                command: "insert",
                schemaName: "mcc",
                queryName: "requestcohorts",
                rows: [{
                    "requestId": objectId,
                    "numberofanimals": data.get("animal-cohorts-" + i + "-numberofanimals"),
                    "sex": data.get("animal-cohorts-" + i + "-sex"),
                    "othercharacteristics": data.get("animal-cohorts-" + i + "-othercharacteristics"),
                }]
            })

            i++
        }

        return commands
    }

    function handleSubmit(e: FormEvent) {
        e.preventDefault()
        setDisplayOverlay(true)
        setStateRollbackOnFailure(requestData.request.status)

        //TODO: check this??
        requestData.request.status = "Submitted"

        const el = e.currentTarget as HTMLFormElement
        const data = new FormData(el)
        el.querySelectorAll<HTMLSelectElement>('select[multiple]').forEach(function(x){
            data.set(x.id, Array.from(x.selectedOptions, option => option.value).join(','))
        })

        const objectId = requestId || nanoid()
        let coinvestigatorCommands = get_coinvestigator_commands(data, objectId)
        let cohortCommands = get_animal_cohort_commands(data, objectId)

        let rowId = requestId ? {"rowid": requestData.request.rowid} : {}

        Query.saveRows({
            commands: [
                {
                    command: requestId ? "update" : "insert",
                    schemaName: "mcc",
                    queryName: "animalrequests",
                    rows: [{
                        "objectId": objectId,
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
                        "researcharea": data.get("research-area"),
                        "otherjustification": data.get("research-area-other-specify"),
                        "existingmarmosetcolony": data.get("existing-marmoset-colony"),
                        "existingnhpfacilities": data.get("existing-nhp-facilities"),
                        "animalwelfare": data.get("animal-welfare"),
                        "certify": data.get("certify"),
                        "vetlastname": data.get("vet-last-name"),
                        "vetfirstname": data.get("vet-first-name"),
                        "vetemail": data.get("vet-email"),
                        "iacucapproval": data.get("iacuc-approval"),
                        "iacucprotocol": data.get("iacuc-protocol"),
                        "grantnumber" : data.get("funding-grant-number"),
                        "applicationduedate": data.get("funding-application-due-date"),
                        "status": requestData.request.status,
                        ...rowId
                    }]
                },
                ...coinvestigatorCommands,
                ...cohortCommands
            ],
            success: function(response) {
                setDisplayOverlay(false)
                if (isSubmitting) {
                    window.location.href = ActionURL.buildURL('mcc', 'mccRequests.view')
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
                    <Input id="investigator-last-name" ariaLabel="Last Name" isSubmitting={isSubmitting} required={getRequired()} placeholder="Last Name" defaultValue={requestData.request.lastname}/>
                </div>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="investigator-first-name" ariaLabel="First Name" isSubmitting={isSubmitting} required={getRequired()} placeholder="First Name" defaultValue={requestData.request.firstname}/>
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
                    <YesNoRadio id="is-early-stage-investigator" ariaLabel="Early Stage Investigator" isSubmitting={isSubmitting} required={getRequired()} defaultValue={requestData.request.earlystageinvestigator}/>
                </div>
            </div>
            </ErrorMessageHandler>

            <ErrorMessageHandler isSubmitting={isSubmitting}>
            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-4">
                <Title text="3. Affiliated research institution*"/>

                <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="institution-name" ariaLabel="Institution Name" isSubmitting={isSubmitting} placeholder="Name" required={getRequired()} defaultValue={requestData.request.institutionname}/>
                </div>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="institution-city" ariaLabel="Institution City" isSubmitting={isSubmitting} placeholder="City" required={getRequired()} defaultValue={requestData.request.institutioncity}/>
                </div>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="institution-state" ariaLabel="Institution State" isSubmitting={isSubmitting} placeholder="State" required={getRequired()} defaultValue={requestData.request.institutionstate}/>
                </div>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="institution-country" ariaLabel="Institution Country" isSubmitting={isSubmitting} placeholder="Country" required={getRequired()} defaultValue={requestData.request.institutioncountry}/>
                </div>

                <Title text="4. Affiliated Research Institution Type*"/>

                <div className="tw-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Select id="institution-type" ariaLabel="Institution Type" isSubmitting={isSubmitting} placeholder="Type" required={getRequired()} defaultValue={requestData.request.institutiontype} options={institutionTypeOptions}/>
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
                        <Input id="official-last-name" ariaLabel="Last Name" isSubmitting={isSubmitting} placeholder="Last Name" required={getRequired()} defaultValue={requestData.request.officiallastname}/>
                    </div>

                    <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="official-first-name" ariaLabel="First Name" isSubmitting={isSubmitting} placeholder="First Name" required={getRequired()} defaultValue={requestData.request.officialfirstname}/>
                    </div>

                    <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="official-email" ariaLabel="Email Address" isSubmitting={isSubmitting} placeholder="Email Address" required={getRequired()} defaultValue={requestData.request.officialemail}/>
                    </div>
                </div>
            </div>
            </ErrorMessageHandler>

            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                <Title text="6. Co-Investigators"/>

                <CoInvestigators isSubmitting={isSubmitting} defaultValue={requestData.coinvestigators} required={getRequired()}/>
            </div>

            <Title text="7. Existing or proposed funding source (select all that apply)"/>
            <Funding id="funding" isSubmitting={isSubmitting} defaultValue={requestData.request} required={getRequired()}/>

            <h3>Institutional Animal Facilities and Capabilities</h3>
            <div className="tw-w-full tw-px-3">
                <Title text="1. Does your institution have existing NHP facilities?"/>
                <ErrorMessageHandler isSubmitting={isSubmitting}>
                    <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-4">
                        <Select id="existing-nhp-facilities" ariaLabel="Existing NHP Facilities" isSubmitting={isSubmitting} options={existingNHPFacilityOptions} defaultValue={requestData.request.existingnhpfacilities} required={getRequired()}/>
                    </div>
                </ErrorMessageHandler>

                <Title text="2. Does your institution have an existing marmoset colony?"/>
                <ErrorMessageHandler isSubmitting={isSubmitting}>
                    <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-4">
                        <Select id="existing-marmoset-colony" ariaLabel="Existing Marmoset Colony" isSubmitting={isSubmitting} options={existingMarmosetColonyOptions} defaultValue={requestData.request.existingmarmosetcolony} required={getRequired()}/>
                    </div>
                </ErrorMessageHandler>

                <Title text="3. Do you plan to breed marmosets?"/>
                <div className="tw-w-full tw-px-3 tw-mb-4">
                    <AnimalBreeding id="animal-breeding" isSubmitting={isSubmitting} defaultValue={requestData.request} required={getRequired()}/>
                </div>
            </div>

            <h3>Research Details</h3>

            <div className="tw-flex tw-flex-wrap tw-mx-2">
                <Title text="1. Research Area"/>

                <div className="tw-w-full tw-px-3 tw-mb-6">
                    <ResearchArea id="research-area" isSubmitting={isSubmitting} defaultValue={requestData.request} required={getRequired()}/>
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-4">
                    <Title text={"2. " + experimentalRationalePlaceholder}/>
                    <Tooltip id="research-use-statement-helper"
                       text={experimentalRationalePlaceholder}
                    />

                    <ErrorMessageHandler isSubmitting={isSubmitting}>
                    <TextArea id="experiment-rationale" ariaLabel="Experimental rationale" isSubmitting={isSubmitting} placeholder={experimentalRationalePlaceholder} required={getRequired()} defaultValue={requestData.request.experimentalrationale}/>
                    </ErrorMessageHandler>
                </div>

                <Title text="3. Animal Cohorts"/>
                <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-6">
                    <AnimalCohorts isSubmitting={isSubmitting} defaultValue={requestData.cohorts} required={getRequired()}/>
                </div>

                <Title text={"4. " + methodsProposedPlaceholder}/>
                <div className="tw-w-full tw-px-3 tw-mb-6">
                    <ErrorMessageHandler isSubmitting={isSubmitting}>
                    <div className="tw-w-full tw-px-3 tw-mb-6">
                        <TextArea id="methods-proposed" ariaLabel="Methods Proposed" isSubmitting={isSubmitting} placeholder={methodsProposedPlaceholder} required={getRequired()} defaultValue={requestData.request.methodsproposed}/>
                    </div>
                    </ErrorMessageHandler>
                </div>

                <Title text={"5. " + collaborationsPlaceholder}/>
                <div className="tw-w-full tw-px-3 tw-mb-6">
                    <ErrorMessageHandler isSubmitting={isSubmitting}>
                    <div className="tw-w-full tw-px-3 tw-mb-6">
                        <TextArea id="collaborations" ariaLabel="Collaborations" isSubmitting={isSubmitting} placeholder={collaborationsPlaceholder} required={getRequired()} defaultValue={requestData.request.collaborations}/>
                    </div>
                    </ErrorMessageHandler>
                </div>

                <Title text={"6. " + animalWellfarePlaceholder}/>
                <div className="tw-w-full tw-px-3 tw-mb-6">
                    <ErrorMessageHandler isSubmitting={isSubmitting}>
                    <div className="tw-w-full tw-px-3 tw-mb-6">
                        <TextArea id="animal-welfare" ariaLabel="Animal Welfare" isSubmitting={isSubmitting} placeholder={animalWellfarePlaceholder} required={getRequired()} defaultValue={requestData.request.animalwelfare}/>
                    </div>
                    </ErrorMessageHandler>

                    <ErrorMessageHandler isSubmitting={isSubmitting}>
                    <div className="tw-w-full tw-px-3 tw-mb-6">
                        <input type="checkbox" name="certify" id="certify" aria-label="Certify" className={(isSubmitting ? "custom-invalid" : "")} required={getRequired()} defaultChecked={requestData.request.certify}/>
                        <label className="tw-text-gray-700 ml-1">{certificationLabel}</label>
                    </div>
                    </ErrorMessageHandler>
                </div>

                <ErrorMessageHandler isSubmitting={isSubmitting}>
                <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-4">
                    <Title text="7. Attending veterinarian"/>

                    <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="vet-last-name" ariaLabel="Last Name" isSubmitting={isSubmitting} placeholder="Last Name" required={getRequired()} defaultValue={requestData.request.vetlastname}/>
                    </div>

                    <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="vet-first-name" ariaLabel="First Name" isSubmitting={isSubmitting} placeholder="First Name" required={getRequired()} defaultValue={requestData.request.vetfirstname}/>
                    </div>

                    <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="vet-email" ariaLabel="Email" isSubmitting={isSubmitting} placeholder="Email Address" required={getRequired()} defaultValue={requestData.request.vetemail}/>
                    </div>
                </div>
                </ErrorMessageHandler>
            </div>

            <IACUCProtocol id="iacuc" isSubmitting={isSubmitting} required={getRequired()} defaultValue={requestData.request}/>

            <div className="tw-flex tw-flex-wrap tw-mx-2">
                <button className="tw-ml-auto tw-bg-red-500 hover:tw-bg-red-400 tw-text-white tw-font-bold tw-py-4 tw-mt-2 tw-px-6 tw-border-none tw-rounded" onClick={(e) => {
                    e.preventDefault()

                    if (confirm("You are about to leave this page.")) {
                        window.location.href = ActionURL.buildURL('mcc', 'mccRequests.view');
                    }
                }}>Cancel</button>

                <Button onClick={() => {
                    handleSubmitButton(false);
                 }} text={getSaveButtonText()} display={hasEditPermission()}/>

                <Button onClick={() => {
                    handleSubmitButton(true);
                 }} text={getSubmitButtonText()} display={hasEditPermission()}/>
            </div>
        </form>

        <SavingOverlay display={displayOverlay} />
        </>
     )
}
