import React, { useState, useRef, FormEvent } from 'react'
import { Query, ActionURL, Filter } from '@labkey/api'
import { nanoid } from 'nanoid'

import Tooltip from './tooltip'
import Title from './title'
import Input from './input'
import Select from './select'
import CoInvestigators from './co-investigators'
import TextArea from './text-area'
import YesNoRadio from './yes-no-radio'
import InputNumber from './input-number'
import ResearchArea from './research-area'

import {
    earlyInvestigatorTooltip, institutionTypeOptions, 
    isPrincipalInvestigatorOptions, fundingSourceOptions,
    experimentalRationalePlaceholder, otherCharacteristicsPlaceholder,
    methodsProposedPlaceholder, collaborationsPlaceholder,
    animalWellfarePlaceholder, signingOfficialHelper,
    certificationLabel, existingMarmosetColonyOptions,
    existingNHPFacilityOptions, IACUCApprovalOptions
} from './values'


export function AnimalRequest() {
    const requestId = (new URLSearchParams(window.location.search)).get("requestId")
    const [rowId, setRowId] = useState(null)
    const [isSubmitting, setIsSubmitting] = useState(false)

    // On submit, state is managed by the FormData object in handleSubmit. These hooks are only used to propagate values
    // from the database into the form via fillForm if there is a requestId
    const [isFormQueried, setIsFormQueried] = useState(false)
    const [animalRequests, setAnimalRequests] = useState({
        "returned": false,
        "data": {}
    })
    const [coinvestigators, setCoinvestigators] = useState({
        "returned": false,
        "data": []
    })


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


    function handleSubmit(e: FormEvent) {
        e.preventDefault()

        const data = new FormData(e.currentTarget)

        const objectId = requestId || nanoid()
        let coinvestigatorCommands = get_coinvestigator_commands(data, objectId)

        let rowId = requestId ? {"rowid": animalRequests.data.rowid} : {}
        console.log(rowId)

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
                        "isbreedinganimals": data.get("is-planning-to-breed-animals"),
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
                        ...rowId
                    }]
                },
                ...coinvestigatorCommands
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
                "iacucapproval"
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

    }

    // TODO: we should scan the URL for requestId=XXXX. If this is provided, make a loading indicator and query LabKey to
    // populate this form with the values from that saved request.

    if (requestId && (animalRequests.returned === false || coinvestigators.returned === false)) {
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
                        <Input id="investigator-last-name" isSubmitting={isSubmitting} required={true} placeholder="Last Name" defaultValue={animalRequests.data.lastname}/>
                    </div>

                    <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="investigator-first-name" isSubmitting={isSubmitting} required={true} placeholder="First Name" defaultValue={animalRequests.data.firstname}/>
                    </div>

                    <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="investigator-middle-initial" isSubmitting={isSubmitting} required={true} placeholder="Middle Initial" defaultValue={animalRequests.data.middleinitial}/>
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
                        <YesNoRadio id="is-principal-investigator" required={true} defaultValue={animalRequests.data.isprincipalinvestigator}/>
                    </div>
                </div>

                <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                    <Title text="3. Affiliated research institution*"/>

                    <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="institution-name" isSubmitting={isSubmitting} placeholder="Name" required={true} defaultValue={animalRequests.data.institutionname}/>
                    </div>

                    <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="institution-city" isSubmitting={isSubmitting} placeholder="City" required={true} defaultValue={animalRequests.data.institutioncity}/>
                    </div>

                    <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="institution-state" isSubmitting={isSubmitting} placeholder="State" required={true} defaultValue={animalRequests.data.institutionstate}/>
                    </div>

                    <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="institution-country" isSubmitting={isSubmitting} placeholder="Country" required={true} defaultValue={animalRequests.data.institutioncountry}/>
                    </div>
                </div>

                <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                    <Title text="4. Institution Type*"/>

                    <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Select id="institution-type" isSubmitting={isSubmitting} options={institutionTypeOptions} defaultValue={animalRequests.data.institutiontype}/>
                    </div>
                </div>

                <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                    <Title text="5. Institution Signing Official*"/>
                    <Tooltip id="signing-official-helper"
                             text={signingOfficialHelper}
                    />

                    <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="official-last-name" isSubmitting={isSubmitting} placeholder="Last Name" required={true} defaultValue={animalRequests.data.officiallastname}/>
                    </div>

                    <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="official-first-name" isSubmitting={isSubmitting} placeholder="First Name" required={true} defaultValue={animalRequests.data.officialfirstname}/>
                    </div>

                    <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="official-email" isSubmitting={isSubmitting} placeholder="Email Address" required={true} defaultValue={animalRequests.data.officialemail}/>
                    </div>
                </div>

                <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                    <Title text="6. Co-investigators"/>

                    <CoInvestigators isSubmitting={isSubmitting} defaultValue={coinvestigators.data}/>
                </div>

                <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                    <Title text="7. Existing or proposed funding source*"/>

                    <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Select id="funding-source" isSubmitting={isSubmitting} options={fundingSourceOptions} required={true} defaultValue={animalRequests.data.fundingsource}/>
                    </div>

                    {/*TODO: if secured, need to capture grant #. If no funding, ask about application due date*/}
                    {/*<div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">*/}
                    {/*    <Select id="grant-number" isSubmitting={isSubmitting} placeholder="Grant Number(s)" required={false}/>*/}
                    {/*</div>*/}
                </div>
                 
                <div className="tw-flex tw-flex-wrap tw-mx-2">
                    {/*TODO: info popup with more guidance*/}
                    <Title text="8. Research Use Statement*"/>

                    <div className="tw-w-full tw-px-3 tw-mb-10">
                        <TextArea id="experiment-rationale" isSubmitting={isSubmitting} placeholder={experimentalRationalePlaceholder} required={true} defaultValue={animalRequests.data.experimentalrationale}/>
                    </div>

                    {/*TODO: this should require at least one animal cohort to be added*/}

                    {/*Treat more like Co-investigators, which is a 1:many relationship. I created the table mcc.requestcohorts. Per cohort, capture discrete: number, sex, characteristics*/}
                    {/*<div className="tw-w-full tw-px-3 tw-mb-10">*/}
                    {/*    <Title text="Number of animals needed:&nbsp;&nbsp;&nbsp;&nbsp;"/>*/}
                    {/*    <InputNumber id="number-of-animals" isSubmitting={isSubmitting} required={true}/>*/}
                    {/*</div>*/}

                    {/*<div className="tw-w-full tw-px-3 tw-mb-10">*/}
                    {/*    <TextArea id="other-characteristics" isSubmitting={isSubmitting} placeholder={otherCharacteristicsPlaceholder} required={true}/>*/}
                    {/*</div>*/}

                    <div className="tw-w-full tw-px-3 tw-mb-10">
                        <TextArea id="methods-proposed" isSubmitting={isSubmitting} placeholder={methodsProposedPlaceholder} required={true} defaultValue={animalRequests.data.methodsproposed}/>
                    </div>

                    <div className="tw-w-full tw-px-3 tw-mb-10">
                        <TextArea id="collaborations" isSubmitting={isSubmitting} placeholder={collaborationsPlaceholder} required={true} defaultValue={animalRequests.data.collaborations}/>
                    </div>

                    <div className="tw-w-full tw-px-3 tw-mb-10">
                        <div className="tw-mb-6">
                            <Title text="Do you plan to breed animals?"/>
                        </div>

                        <div className="tw-mb-6">
                            <YesNoRadio id="is-planning-to-breed-animals" required={true} defaultValue={animalRequests.data.isbreedinganimals}/>
                        </div>

                        {/*If is-planning-to-breed-animals is true, show field asking for free-text description of purpose*/}
                        {/*<div className="tw-mb-6">*/}

                        {/*</div>*/}
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
                            <Select id="existing-marmoset-colony" isSubmitting={isSubmitting} options={existingMarmosetColonyOptions} defaultValue={animalRequests.data.existingmarmosetcolony}/>
                        </div>

                        <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                            <Title text="Does your institution have existing NHP facilities?"/>
                            <Select id="existing-nhp-facilities" isSubmitting={isSubmitting} options={existingNHPFacilityOptions} defaultValue={animalRequests.data.existingnhpfacilities}/>
                        </div>
                    </div>

                    <div className="tw-w-full tw-px-3 tw-mb-6">
                        <div className="tw-w-full tw-px-3 tw-mb-6">
                            <TextArea id="animal-welfare" isSubmitting={isSubmitting} placeholder={animalWellfarePlaceholder} required={true} defaultValue={animalRequests.data.animalwelfare}/>
                        </div>

                        <div className="tw-w-full tw-px-3 tw-mb-6">
                            <input type="checkbox" name="certify" required={true} defaultChecked={animalRequests.data.certify}/>
                            <label className="tw-text-gray-700 ml-1">{certificationLabel}</label>
                        </div>
                    </div>

                    <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                        <Title text="Attending veterinarian"/>

                        <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                            <Input id="vet-last-name" isSubmitting={isSubmitting} placeholder="Last Name" required={true} defaultValue={animalRequests.data.vetlastname}/>
                        </div>

                        <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                            <Input id="vet-first-name" isSubmitting={isSubmitting} placeholder="First Name" required={true} defaultValue={animalRequests.data.vetfirstname}/>
                        </div>

                        <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                            <Input id="vet-email" isSubmitting={isSubmitting} placeholder="Email Address" required={true} defaultValue={animalRequests.data.vetemail}/>
                        </div>
                    </div>

                    <div className="tw-flex tw-flex-wrap tw-mx-2">
                        <Title text="IACUC Approval"/>

                        <div className="tw-w-full tw-px-3 md:tw-mb-0">
                            <Select id="iacuc-approval" isSubmitting={isSubmitting} options={IACUCApprovalOptions} required={true} defaultValue={animalRequests.data.iacucapproval}/>
                        </div>

                        {/*TODO: this is required if iacuc-approval == approved. It's a free-text field*/}
                        {/*<div className="tw-w-full tw-px-3 md:tw-mb-0">*/}
                        {/*    <Select id="iacuc-protocol" isSubmitting={isSubmitting} placeholder="IACUC Protocol Number" required={false}/>*/}
                        {/*</div>*/}
                    </div>
                </div>

                <div className="tw-flex tw-flex-wrap tw-mx-2">
                    {/*TODO the spacing is weird here*/}
                    <button className="tw-ml-auto tw-bg-blue-500 hover:tw-bg-blue-400 tw-text-white tw-font-bold tw-py-4 tw-mt-2 tw-px-6 tw-border-none tw-rounded" onClick={() => setIsSubmitting(true)}>Submit</button>
                    <button className="tw-ml-auto tw-bg-blue-500 hover:tw-bg-blue-400 tw-text-white tw-font-bold tw-py-4 tw-mt-2 tw-px-6 tw-border-none tw-rounded" onClick={() => {
                        //TODO: make some kind of 'Are you sure you want exit?' confirmation, and if the user picks yes, then:
                        window.location.href = ActionURL.buildURL('mcc', 'mccRequests.view');
                    }}>Cancel</button>
                </div>
            </form>
         )
     }
}
