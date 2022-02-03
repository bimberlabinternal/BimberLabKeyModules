import React, { useState, FormEvent } from 'react'
import { Query, ActionURL, Filter, getServerContext } from '@labkey/api';
import { nanoid } from 'nanoid'

import Tooltip from './tooltip'
import Title from './title'
import Input from './input'
import Select from './select'
import CoInvestigators from './co-investigators'
import TextArea from './text-area'
import YesNoRadio from './yes-no-radio'
import AnimalBreeding from './animal-breeding'
import IACUCProtocol from './iacuc-protocol'
import Funding from './funding'
import ResearchArea from './research-area'
import AnimalCohorts from './animal-cohort'
import Button from './button'
import SavingOverlay from './saving-overlay'
import ErrorMessageHandler from './error-message-handler'

import {
    earlyInvestigatorTooltip,
    experimentalRationalePlaceholder,
    methodsProposedPlaceholder, collaborationsPlaceholder,
    animalWellfarePlaceholder, signingOfficialTooltip,
    certificationLabel, existingMarmosetColonyOptions,
    existingNHPFacilityOptions,
    researchUseStatementTooltip
} from './values'


export function AnimalRequest() {
    const requestId = (new URLSearchParams(window.location.search)).get("requestId")
    const [isSubmitting, setIsSubmitting] = useState(false)
    const [displayOverlay, setDisplayOverlay] = useState(false)

    // On submit, state is managed by the FormData object in handleSubmit. These hooks are only used to propagate values
    // from the database into the form via fillForm if there is a requestId
    const [isFormQueried, setIsFormQueried] = useState(false)
    const [animalRequests, setAnimalRequests] = useState({
        "returned": false,
        "data": {"status": "draft",
            middleinitial: undefined,
            lastname: undefined,
            firstname: undefined,
            isprincipalinvestigator: undefined,
            institutionname: undefined,
            institutioncity: undefined,
            institutionstate: undefined,
            institutioncountry: undefined,
            officiallastname: undefined,
            officialfirstname: undefined,
            officialemail: undefined,
            experimentalrationale: undefined,
            methodsproposed: undefined,
            collaborations: undefined,
            existingmarmosetcolony: undefined,
            existingnhpfacilities: undefined,
            rowid: undefined,
            certify: false,
            animalwelfare: undefined,
            vetlastname: undefined,
            vetemail: undefined,
            vetfirstname: undefined
        }
    })
    const [coinvestigators, setCoinvestigators] = useState({
        "returned": false,
        "data": []
    })
    const [animalCohorts, setAnimalCohorts] = useState({
        "returned": false,
        "data": [{uuid: nanoid(), rowid: undefined, othercharacteristics: undefined}]
    })
    

    function handleFailure(response) {
        //TODO: actually do something with this!
        console.error(response)
        console.error(response.exception)  //this is probably what you want to show. An example would be to submit data with a long value for middle initial (>14 characters)
    }

    
    function getRequired() {
        switch (animalRequests.data.status) {
            case "draft":
                return false
            case "submitting":
                return true
            case "submitted":
                return false
            case "approving":
                return true
            case "under-review":
                return true
            case "approving-final":
                return true
            case "rejecting":
                return true
            default:
                return false
        }
    }

    // The general idea is that users with MCCRequestAdminPermission can edit all states.
    // A normal user can only edit their own requests, and only when in draft form. Once submitted, they can no longer edit them.
    function hasEditPermission() {
        const ctx = getServerContext().getModuleContext('mcc') || {};
        if (!!ctx.hasRequestAdminPermission) {
            return true
        }

        if (!animalRequests.data.status) {
            return true
        }

        return "draft" === animalRequests.data.status
    }

    function handleNextStateSubmitButton() {
        setIsSubmitting(true);

        if (animalRequests.data.status === "draft") {
            setAnimalRequests({
                    "returned": true,
                    "data": { ...animalRequests.data, status:"submitting" }
            });
        } else if (animalRequests.data.status === "submitted") {
            setAnimalRequests({
                    "returned": true,
                    "data": { ...animalRequests.data, status:"approving" }
            });
        } else if (animalRequests.data.status === "under-review") {
            setAnimalRequests({
                    "returned": true,
                    "data": { ...animalRequests.data, status:"approving-final" }
            });
        }
    }

    function getSubmitButtonText() {
        switch (animalRequests.data.status) {
            case "draft":
                return "Submit"
            case "submitting":
                return "Submit"
            case "submitted":
                return "Approve Request"
            case "approving":
                return "Approve Request"
            case "under-review":
                return "Approve"
            case "approving-final":
                return "Approve"
            case "rejecting":
                return "Approve"
            default:
                "Submit"
        }
    } 


    function handleNextStateSaveButton() {
        setIsSubmitting(false);

        if (animalRequests.data.status === "submitting") {
            setAnimalRequests({
                    "returned": true,
                    "data": { ...animalRequests.data, status:"draft" }
            });
        } else if (animalRequests.data.status === "approving") {
            setAnimalRequests({
                    "returned": true,
                    "data": { ...animalRequests.data, status:"submitted" }
            });
        } else if (animalRequests.data.status === "under-review") {
            setIsSubmitting(true);
            setAnimalRequests({
                    "returned": true,
                    "data": { ...animalRequests.data, status:"rejecting" }
            });
        }
    }

    function getSaveButtonText() {
        switch (animalRequests.data.status) {
            case "draft":
                return "Save"
            case "submitting":
                return "Save"
            case "submitted":
                return "Save"
            case "approving": 
                return "Save"
            case "under-review":
                return "Reject"
            case "approving-final":
                return "Reject"
            case "rejecting":
                return "Reject"
            default:
                "Save"
        }
    }


    function get_coinvestigator_commands(data, objectId) {
        let commands = []
        let i = 0

        if(requestId && coinvestigators.data.length > 0) {
            for(const coinvestigator of coinvestigators.data) {
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

        if(requestId && animalCohorts.data.length > 0) {
            for(const cohort of animalCohorts.data) {
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

        if(animalRequests.data.status === "submitting") {
            animalRequests.data.status = "submitted"
        } else if(animalRequests.data.status === "approving") {
            animalRequests.data.status = "under-review"
        } else if(animalRequests.data.status === "approving-final") {
            animalRequests.data.status = "approved"
        } else if(animalRequests.data.status === "rejecting") {
            animalRequests.data.status = "rejected"
        }

        const data = new FormData(e.currentTarget as HTMLFormElement)

        const objectId = requestId || nanoid()
        let coinvestigatorCommands = get_coinvestigator_commands(data, objectId)
        let cohortCommands = get_animal_cohort_commands(data, objectId)

        let rowId = requestId ? {"rowid": animalRequests.data.rowid} : {}

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
                        "isprincipalinvestigator": data.get("is-principal-investigator"),
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
                        "status": animalRequests.data.status,
                        ...rowId
                    }]
                },
                ...coinvestigatorCommands,
                ...cohortCommands
            ],
            success: function(response) {
                window.location.href = ActionURL.buildURL('mcc', 'mccRequests.view')
            },
            failure: handleFailure
        })
    }


    function fillForm() {
        setIsFormQueried(true)

        Query.selectRows({
            schemaName: "mcc",
            queryName: "animalrequests",
            columns: [ 
                "rowid",
                "objectId",
                "lastname",
                "firstname",
                "middleinitial",
                "isprincipalinvestigator",
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
            success: function(resp) {
                let returnedData = resp.rows[0]

                setAnimalRequests({
                    "returned": true,
                    "data": returnedData
                })
            },
            failure: handleFailure
        })


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
            success: function(resp) {
                let returnedData = resp.rows
                setCoinvestigators({
                    "returned": true,
                    "data": returnedData
                })
            },
            failure: handleFailure
        })


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
            success: function(resp) {
                let returnedData = resp.rows
                setAnimalCohorts({
                    "returned": true,
                    "data": returnedData
                })
            },
            failure: handleFailure
        })

    }


    if (requestId && (animalRequests.returned === false || coinvestigators.returned === false || animalCohorts.returned === false)) {
        if (isFormQueried === false) {
            fillForm()
        }

        return (
           <div className="tw-flex tw-justify-center tw-items-center">
             <div style={{ borderBottom: "2px solid #3495d2" }} className="tw-animate-spin tw-rounded-full tw-h-32 tw-w-32"></div>
           </div>
        )
    } else if (requestId && animalRequests.returned === true && animalRequests.data === undefined) {
        return(<Title text="No such request."/>)
    } else {
        return (
            <>
            <form className="tw-w-full tw-max-w-4xl" onSubmit={handleSubmit} autoComplete="off">
                <ErrorMessageHandler isSubmitting={isSubmitting}>
                <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-4">
                    <Title text="Principal Investigator*"/>

                    <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="investigator-last-name" ariaLabel="Last Name" isSubmitting={isSubmitting} required={getRequired()} placeholder="Last Name" defaultValue={animalRequests.data.lastname}/>
                    </div>

                    <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="investigator-first-name" ariaLabel="First Name" isSubmitting={isSubmitting} required={getRequired()} placeholder="First Name" defaultValue={animalRequests.data.firstname}/>
                    </div>

                    <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="investigator-middle-initial" isSubmitting={isSubmitting} required={false} placeholder="Middle Initial" defaultValue={animalRequests.data.middleinitial}/>
                    </div>
                </div>
                </ErrorMessageHandler>

                <ErrorMessageHandler isSubmitting={isSubmitting}>
                <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-4">
                    <div className="tw-relative tw-w-full tw-mb-6 md:tw-mb-0">
                        <Title text="Are you an early-stage investigator?&nbsp;"/>
                        <Tooltip id="early-stage-investigator-helper"
                           text={earlyInvestigatorTooltip}
                        />
                    </div>

                    <div className="tw-w-full tw-px-3 tw-mt-6">
                        <YesNoRadio id="is-principal-investigator" ariaLabel="Principal Investigator" isSubmitting={isSubmitting} required={getRequired()} defaultValue={animalRequests.data.isprincipalinvestigator}/>
                    </div>
                </div>
                </ErrorMessageHandler>

                <ErrorMessageHandler isSubmitting={isSubmitting}>
                <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-4">
                    <Title text="Affiliated research institution*"/>

                    <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="institution-name" ariaLabel="Institution Name" isSubmitting={isSubmitting} placeholder="Name" required={getRequired()} defaultValue={animalRequests.data.institutionname}/>
                    </div>

                    <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="institution-city" ariaLabel="Institution City" isSubmitting={isSubmitting} placeholder="City" required={getRequired()} defaultValue={animalRequests.data.institutioncity}/>
                    </div>

                    <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="institution-state" ariaLabel="Institution State" isSubmitting={isSubmitting} placeholder="State" required={getRequired()} defaultValue={animalRequests.data.institutionstate}/>
                    </div>

                    <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="institution-country" ariaLabel="Institution Country" isSubmitting={isSubmitting} placeholder="Country" required={getRequired()} defaultValue={animalRequests.data.institutioncountry}/>
                    </div>
                </div>
                </ErrorMessageHandler>

                <ErrorMessageHandler isSubmitting={isSubmitting}>
                <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-4">
                    <div className="tw-relative tw-w-full tw-mb-6 md:tw-mb-0">
                        <Title text="Institution Signing Official*&nbsp;"/>
                        <Tooltip id="signing-official-helper"
                             text={signingOfficialTooltip}
                        />
                    </div>


                    <div className="tw-flex tw-flex-wrap tw-mt-6">
                        <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                            <Input id="official-last-name" ariaLabel="Last Name" isSubmitting={isSubmitting} placeholder="Last Name" required={getRequired()} defaultValue={animalRequests.data.officiallastname}/>
                        </div>

                        <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                            <Input id="official-first-name" ariaLabel="First Name" isSubmitting={isSubmitting} placeholder="First Name" required={getRequired()} defaultValue={animalRequests.data.officialfirstname}/>
                        </div>

                        <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                            <Input id="official-email" ariaLabel="Email Address" isSubmitting={isSubmitting} placeholder="Email Address" required={getRequired()} defaultValue={animalRequests.data.officialemail}/>
                        </div>
                    </div>
                </div>
                </ErrorMessageHandler>

                <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                    <Title text="Co-investigators"/>

                    <CoInvestigators isSubmitting={isSubmitting} defaultValue={coinvestigators.data} required={getRequired()}/>
                </div>

                <Funding id="funding" isSubmitting={isSubmitting} defaultValue={animalRequests.data} required={getRequired()}/>

                <div className="tw-flex tw-flex-wrap tw-mx-2">
                    <div className="tw-relative tw-w-full tw-mb-6 md:tw-mb-0">
                        <Title text="Research Use Statement*&nbsp;"/>
                        <Tooltip id="research-use-statement-helper"
                           text={experimentalRationalePlaceholder}
                        />
                    </div>

                    <ErrorMessageHandler isSubmitting={isSubmitting}>
                    <div className="tw-w-full tw-px-3 tw-mb-6 tw-mt-6">
                        <TextArea id="experiment-rationale" ariaLabel="Experimental rationale" isSubmitting={isSubmitting} placeholder={experimentalRationalePlaceholder} required={getRequired()} defaultValue={animalRequests.data.experimentalrationale}/>
                    </div>
                    </ErrorMessageHandler>

                    <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-6">
                        <Title text="Animal Cohorts"/>

                        <AnimalCohorts isSubmitting={isSubmitting} defaultValue={animalCohorts.data} required={getRequired()}/>
                    </div>

                    <ErrorMessageHandler isSubmitting={isSubmitting}>
                    <div className="tw-w-full tw-px-3 tw-mb-4">
                        <TextArea id="methods-proposed" ariaLabel="Methods Proposed" isSubmitting={isSubmitting} placeholder={methodsProposedPlaceholder} required={getRequired()} defaultValue={animalRequests.data.methodsproposed}/>
                    </div>
                    </ErrorMessageHandler>

                    <ErrorMessageHandler isSubmitting={isSubmitting}>
                    <div className="tw-w-full tw-px-3 tw-mb-4">
                        <TextArea id="collaborations" ariaLabel="Collaborations" isSubmitting={isSubmitting} placeholder={collaborationsPlaceholder} required={getRequired()} defaultValue={animalRequests.data.collaborations}/>
                    </div>
                    </ErrorMessageHandler>

                    <div className="tw-w-full tw-px-3 tw-mb-4">
                        <div className="tw-mb-6">
                            <Title text="Do you plan to breed animals?"/>
                        </div>
                        <AnimalBreeding id="animal-breeding" isSubmitting={isSubmitting} defaultValue={animalRequests.data} required={getRequired()}/>
                    </div>

                    <ResearchArea id="research-area" isSubmitting={isSubmitting} defaultValue={animalRequests.data} required={getRequired()}/>

                    <div className="tw-w-full tw-px-3">
                        <div className="tw-mb-6">
                            <Title text="Institutional Animal Facilities and Capabilities"/>
                        </div>

                        <ErrorMessageHandler isSubmitting={isSubmitting}>
                        <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-4">
                            <Title text="Does your institution have an existing marmoset colony?"/>
                            <Select id="existing-marmoset-colony" ariaLabel="Existing Marmoset Colony" isSubmitting={isSubmitting} options={existingMarmosetColonyOptions} defaultValue={animalRequests.data.existingmarmosetcolony} required={getRequired()}/>
                        </div>
                        </ErrorMessageHandler>

                        <ErrorMessageHandler isSubmitting={isSubmitting}>
                        <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-4">
                            <Title text="Does your institution have existing NHP facilities?"/>
                            <Select id="existing-nhp-facilities" ariaLabel="Existing NHP Facilities" isSubmitting={isSubmitting} options={existingNHPFacilityOptions} defaultValue={animalRequests.data.existingnhpfacilities} required={getRequired()}/>
                        </div>
                        </ErrorMessageHandler>
                    </div>

                    <div className="tw-w-full tw-px-3 tw-mb-6">
                        <ErrorMessageHandler isSubmitting={isSubmitting}>
                        <div className="tw-w-full tw-px-3 tw-mb-6">
                            <TextArea id="animal-welfare" ariaLabel="Animal Welfare" isSubmitting={isSubmitting} placeholder={animalWellfarePlaceholder} required={getRequired()} defaultValue={animalRequests.data.animalwelfare}/>
                        </div>
                        </ErrorMessageHandler>

                        <ErrorMessageHandler isSubmitting={isSubmitting}>
                        <div className="tw-w-full tw-px-3 tw-mb-6">
                            <input type="checkbox" name="certify" id="certify" aria-label="Certify" className={(isSubmitting ? "custom-invalid" : "")} required={getRequired()} defaultChecked={animalRequests.data.certify}/>
                            <label className="tw-text-gray-700 ml-1">{certificationLabel}</label>
                        </div>
                        </ErrorMessageHandler>
                    </div>

                    <ErrorMessageHandler isSubmitting={isSubmitting}>
                    <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-4">
                        <Title text="Attending veterinarian"/>

                        <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                            <Input id="vet-last-name" ariaLabel="Last Name" isSubmitting={isSubmitting} placeholder="Last Name" required={getRequired()} defaultValue={animalRequests.data.vetlastname}/>
                        </div>

                        <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                            <Input id="vet-first-name" ariaLabel="First Name" isSubmitting={isSubmitting} placeholder="First Name" required={getRequired()} defaultValue={animalRequests.data.vetfirstname}/>
                        </div>

                        <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                            <Input id="vet-email" ariaLabel="Email" isSubmitting={isSubmitting} placeholder="Email Address" required={getRequired()} defaultValue={animalRequests.data.vetemail}/>
                        </div>
                    </div>
                    </ErrorMessageHandler>
                </div>
                
                <IACUCProtocol id="iacuc" isSubmitting={isSubmitting} required={getRequired()} defaultValue={animalRequests.data}/>

                <div className="tw-flex tw-flex-wrap tw-mx-2">
                    <button className="tw-ml-auto tw-bg-red-500 hover:tw-bg-red-400 tw-text-white tw-font-bold tw-py-4 tw-mt-2 tw-px-6 tw-border-none tw-rounded" onClick={(e) => {
                        e.preventDefault()

                        if (confirm("You are about to leave this page.")) {
                            window.location.href = ActionURL.buildURL('mcc', 'mccRequests.view');
                        }
                    }}>Cancel</button>

                    <Button onClick={() => {
                        handleNextStateSaveButton();
                     }} text={getSaveButtonText()} display={hasEditPermission() && animalRequests.data.status != "rejected"}/>

                    <Button onClick={() => {
                        handleNextStateSubmitButton();
                     }} text={getSubmitButtonText()} display={hasEditPermission() && animalRequests.data.status != "rejected"}/>
                </div>
            </form>

            <SavingOverlay display={displayOverlay} />
            </>
         )
     }
}
