import React, { useState, FormEvent } from 'react'
import { Query, ActionURL, Filter } from '@labkey/api'
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

import {
    earlyInvestigatorTooltip, institutionTypeOptions, 
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

    // On submit, state is managed by the FormData object in handleSubmit. These hooks are only used to propagate values
    // from the database into the form via fillForm if there is a requestId
    const [isFormQueried, setIsFormQueried] = useState(false)
    const [animalRequests, setAnimalRequests] = useState({
        "returned": false,
        "data": {"status": "draft"}
    })
    const [coinvestigators, setCoinvestigators] = useState({
        "returned": false,
        "data": []
    })
    const [animalCohorts, setAnimalCohorts] = useState({
        "returned": false,
        "data": [new Set([{"uuid": nanoid()}])]
    })
    
    
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

        while(data.get("coinvestigators-" + i + "-lastName")) {
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

        while(data.get("animal-cohorts-" + i + "-numberofanimals")) {
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

        if(animalRequests.data.status === "submitting") {
            animalRequests.data.status = "submitted"
        } else if(animalRequests.data.status === "approving") {
            animalRequests.data.status = "under-review"
        } else if(animalRequests.data.status === "approving-final") {
            animalRequests.data.status = "approved"
        } else if(animalRequests.data.status === "rejecting") {
            animalRequests.data.status = "rejected"
        }

        const data = new FormData(e.currentTarget)

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
                        "numberofanimals": data.get("number-of-animals"),
                        "othercharacteristics": data.get("other-characteristics"),
                        "methodsproposed": data.get("methods-proposed"),
                        "collaborations": data.get("collaborations"),
                        "isbreedinganimals": data.get("animal-breeding-is-planning-to-breed-animals"),
                        "breedingpurpose": data.get("animal-breeding-purpose"),
                        "ofinterestcenters": data.get("of-interest-centers"),
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
            success: function(data) {
                window.location.href = ActionURL.buildURL('mcc', 'mccRequests.view')
            },
            failure: function(data) {
                //TODO: we should have a standard way to handle errors. Examples of this in LabKey are:
                // https://github.com/LabKey/labkey-ui-components/blob/fa00d0c3f9/packages/components/src/internal/util/utils.ts#L627
                // or ErrorBoundary: https://github.com/LabKey/labkey-ui-components/blob/fa00d0c3f9/packages/components/src/internal/components/error/ErrorBoundary.tsx
                alert("Your data could not be saved.")
                console.error(data)
            }
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
                "numberofanimals",
                "othercharacteristics",
                "methodsproposed",
                "collaborations",
                "isbreedinganimals",
                "breedingpurpose",
                "ofinterestcenters",
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
            failure: function(data) {
                //TODO: we should have a standard way to handle errors. Examples of this in LabKey are:
                // https://github.com/LabKey/labkey-ui-components/blob/fa00d0c3f9/packages/components/src/internal/util/utils.ts#L627
                // or ErrorBoundary: https://github.com/LabKey/labkey-ui-components/blob/fa00d0c3f9/packages/components/src/internal/components/error/ErrorBoundary.tsx
                alert("Your data could not be selected.")
                console.error(data)
            }
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
            failure: function(data) {
                //TODO: we should have a standard way to handle errors. Examples of this in LabKey are:
                // https://github.com/LabKey/labkey-ui-components/blob/fa00d0c3f9/packages/components/src/internal/util/utils.ts#L627
                // or ErrorBoundary: https://github.com/LabKey/labkey-ui-components/blob/fa00d0c3f9/packages/components/src/internal/components/error/ErrorBoundary.tsx
                alert("Your data could not be selected.")
                console.error(data)
            }
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
            failure: function(data) {
                //TODO: we should have a standard way to handle errors. Examples of this in LabKey are:
                // https://github.com/LabKey/labkey-ui-components/blob/fa00d0c3f9/packages/components/src/internal/util/utils.ts#L627
                // or ErrorBoundary: https://github.com/LabKey/labkey-ui-components/blob/fa00d0c3f9/packages/components/src/internal/components/error/ErrorBoundary.tsx
                alert("Your data could not be selected.")
                console.error(data)
            }
        })

    }


    if (requestId && (animalRequests.returned === false || coinvestigators.returned === false || animalCohorts.returned === false)) {
        //TODO Don't crash if the requestId doesn't exist
        //TODO Values
        //TODO Styling
        if (isFormQueried === false) {
            fillForm()
        }

        return (
           <div className="tw-flex tw-justify-center tw-items-center">
             <div style={{ borderBottom: "2px solid #3495d2" }} className="tw-animate-spin tw-rounded-full tw-h-32 tw-w-32"></div>
           </div>
        )
    } else {
        return (
            <form className="tw-w-full tw-max-w-4xl" onSubmit={handleSubmit} autoComplete="off">
                <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                    <Title text="1. Principal Investigator*"/>

                    <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="investigator-last-name" isSubmitting={isSubmitting} required={getRequired()} placeholder="Last Name" defaultValue={animalRequests.data.lastname}/>
                    </div>

                    <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="investigator-first-name" isSubmitting={isSubmitting} required={getRequired()} placeholder="First Name" defaultValue={animalRequests.data.firstname}/>
                    </div>

                    <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="investigator-middle-initial" isSubmitting={isSubmitting} required={getRequired()} placeholder="Middle Initial" defaultValue={animalRequests.data.middleinitial}/>
                    </div>
                </div>

                <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                    <div className="tw-relative tw-w-full tw-mb-6 md:tw-mb-0">
                        <Title text="2. Are you an early-stage investigator?&nbsp;"/>
                        <Tooltip id="early-stage-investigator-helper"
                           text={earlyInvestigatorTooltip}
                        />
                    </div>


                    <div className="tw-w-full tw-px-3 tw-mt-6">
                        <YesNoRadio id="is-principal-investigator" required={getRequired()} defaultValue={animalRequests.data.isprincipalinvestigator}/>
                    </div>
                </div>

                <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                    <Title text="3. Affiliated research institution*"/>

                    <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="institution-name" isSubmitting={isSubmitting} placeholder="Name" required={getRequired()} defaultValue={animalRequests.data.institutionname}/>
                    </div>

                    <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="institution-city" isSubmitting={isSubmitting} placeholder="City" required={getRequired()} defaultValue={animalRequests.data.institutioncity}/>
                    </div>

                    <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="institution-state" isSubmitting={isSubmitting} placeholder="State" required={getRequired()} defaultValue={animalRequests.data.institutionstate}/>
                    </div>

                    <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="institution-country" isSubmitting={isSubmitting} placeholder="Country" required={getRequired()} defaultValue={animalRequests.data.institutioncountry}/>
                    </div>
                </div>

                <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                    <Title text="4. Institution Type*"/>

                    <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Select id="institution-type" isSubmitting={isSubmitting} options={institutionTypeOptions} defaultValue={animalRequests.data.institutiontype} required={getRequired()}/>
                    </div>
                </div>

                <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                    <div className="tw-relative tw-w-full tw-mb-6 md:tw-mb-0">
                        <Title text="5. Institution Signing Official*&nbsp;"/>
                        <Tooltip id="signing-official-helper"
                             text={signingOfficialTooltip}
                        />
                    </div>

                    <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0 tw-mt-6">
                        <Input id="official-last-name" isSubmitting={isSubmitting} placeholder="Last Name" required={getRequired()} defaultValue={animalRequests.data.officiallastname}/>
                    </div>

                    <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="official-first-name" isSubmitting={isSubmitting} placeholder="First Name" required={getRequired()} defaultValue={animalRequests.data.officialfirstname}/>
                    </div>

                    <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="official-email" isSubmitting={isSubmitting} placeholder="Email Address" required={getRequired()} defaultValue={animalRequests.data.officialemail}/>
                    </div>
                </div>

                <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                    <Title text="6. Co-investigators"/>

                    <CoInvestigators isSubmitting={isSubmitting} defaultValue={coinvestigators.data} required={getRequired()}/>
                </div>

                <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                    <Title text="7. Existing or proposed funding source*"/>

                    <Funding id="funding" isSubmitting={isSubmitting} defaultValue={animalRequests.data} required={getRequired()}/>
                </div>
                 
                <div className="tw-flex tw-flex-wrap tw-mx-2">
                    <div className="tw-relative tw-w-full tw-mb-6 md:tw-mb-0">
                        <Title text="8. Research Use Statement*&nbsp;"/>
                        <Tooltip id="research-use-statement-helper"
                           text={researchUseStatementTooltip}
                        />
                    </div>

                    <div className="tw-w-full tw-px-3 tw-mb-6 tw-mt-6">
                        <TextArea id="experiment-rationale" isSubmitting={isSubmitting} placeholder={experimentalRationalePlaceholder} required={getRequired()} defaultValue={animalRequests.data.experimentalrationale}/>
                    </div>

                    <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-6">
                        <Title text="Animal Cohorts"/>

                        <AnimalCohorts isSubmitting={isSubmitting} defaultValue={animalCohorts.data}/>
                    </div>

                    <div className="tw-w-full tw-px-3 tw-mb-10">
                        <TextArea id="methods-proposed" isSubmitting={isSubmitting} placeholder={methodsProposedPlaceholder} required={getRequired()} defaultValue={animalRequests.data.methodsproposed}/>
                    </div>

                    <div className="tw-w-full tw-px-3 tw-mb-10">
                        <TextArea id="collaborations" isSubmitting={isSubmitting} placeholder={collaborationsPlaceholder} required={getRequired()} defaultValue={animalRequests.data.collaborations}/>
                    </div>

                    <div className="tw-w-full tw-px-3 tw-mb-4">
                        <div className="tw-mb-6">
                            <Title text="Do you plan to breed animals?"/>
                        </div>
                        <AnimalBreeding id="animal-breeding" isSubmitting={isSubmitting} defaultValue={animalRequests.data} required={getRequired()}/>
                    </div>

                    <div className="tw-w-full tw-px-3 tw-mb-6">
                        <div className="tw-mb-6">
                            <Title text="Research Area"/>
                        </div>
                        <ResearchArea id="research-area" isSubmitting={isSubmitting} defaultValue={animalRequests.data}/>
                    </div>

                    <div className="tw-w-full tw-px-3">
                        <div className="tw-mb-6">
                            <Title text="Institutional Animal Facilities and Capabilities"/>
                        </div>

                        <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                            <Title text="Does your institution have an existing marmoset colony?"/>
                            <Select id="existing-marmoset-colony" isSubmitting={isSubmitting} options={existingMarmosetColonyOptions} defaultValue={animalRequests.data.existingmarmosetcolony} required={getRequired()}/>
                        </div>

                        <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                            <Title text="Does your institution have existing NHP facilities?"/>
                            <Select id="existing-nhp-facilities" isSubmitting={isSubmitting} options={existingNHPFacilityOptions} defaultValue={animalRequests.data.existingnhpfacilities} required={getRequired()}/>
                        </div>
                    </div>

                    <div className="tw-w-full tw-px-3 tw-mb-6">
                        <div className="tw-w-full tw-px-3 tw-mb-6">
                            <TextArea id="animal-welfare" isSubmitting={isSubmitting} placeholder={animalWellfarePlaceholder} required={getRequired()} defaultValue={animalRequests.data.animalwelfare}/>
                        </div>

                        <div className="tw-w-full tw-px-3 tw-mb-6">
                            <input type="checkbox" name="certify" required={getRequired()} defaultChecked={animalRequests.data.certify}/>
                            <label className="tw-text-gray-700 ml-1">{certificationLabel}</label>
                        </div>
                    </div>

                    <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                        <Title text="Attending veterinarian"/>

                        <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                            <Input id="vet-last-name" isSubmitting={isSubmitting} placeholder="Last Name" required={getRequired()} defaultValue={animalRequests.data.vetlastname}/>
                        </div>

                        <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                            <Input id="vet-first-name" isSubmitting={isSubmitting} placeholder="First Name" required={getRequired()} defaultValue={animalRequests.data.vetfirstname}/>
                        </div>

                        <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                            <Input id="vet-email" isSubmitting={isSubmitting} placeholder="Email Address" required={getRequired()} defaultValue={animalRequests.data.vetemail}/>
                        </div>
                    </div>

                    <div className="tw-flex tw-flex-wrap tw-mx-2">
                        <Title text="IACUC Approval"/>

                        <div className="tw-w-full tw-px-3 md:tw-mb-0">
                            <IACUCProtocol id="iacuc" isSubmitting={isSubmitting} required={getRequired()} defaultValue={animalRequests.data}/>
                        </div>
                    </div>
                </div>

                <div className="tw-flex tw-flex-wrap tw-mx-2">
                    <button className="tw-ml-auto tw-bg-red-500 hover:tw-bg-red-400 tw-text-white tw-font-bold tw-py-4 tw-mt-2 tw-px-6 tw-border-none tw-rounded" onClick={(e) => {
                        e.preventDefault()

                        if (confirm("You are about to leave this page.")) {
                            window.location.href = ActionURL.buildURL('mcc', 'mccRequests.view');
                        }
                    }}>Cancel</button>

                    <Button onClick={() => {
                        handleNextStateSaveButton();
                     }} text={getSaveButtonText()} display={animalRequests.data.status != "rejected" && animalRequests.data.status != "approved"}/>

                    <Button onClick={() => {
                        handleNextStateSubmitButton();
                     }} text={getSubmitButtonText()} display={animalRequests.data.status != "rejected" && animalRequests.data.status != "approved"}/>
                </div>
            </form>
         )
     }
}
