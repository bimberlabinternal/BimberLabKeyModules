import React, { useState, FormEvent } from 'react'
import { Query } from '@labkey/api'
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
    ofInterestCentersPlaceholder, animalWellfarePlaceholder,
    certificationLabel, existingMarmosetColonyOptions,
    existingNHPFacilityOptions, IACUCApprovalOptions
} from './values'


function get_coinvestigator_commands(data, objectId) {
    let coinvestigators = []
    let i = 0

    while(data.get("coinvestigators-" + i + "-lastName")) {
        coinvestigators.push({
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

    return coinvestigators
}


function handleSubmit(e: FormEvent) {
    e.preventDefault();

    const data = new FormData(e.currentTarget)

    const objectId = nanoid()
    let coinvestigatorCommands = get_coinvestigator_commands(data, objectId)

    Query.saveRows({
        commands: [
            {
                command: "insert",
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
                    "iacucapproval": data.get("iacuc-approval")
                }]
            },
            ...coinvestigatorCommands
        ],
        success: function(data) {
            alert("Your data was saved successfully.")
        },
        failure: function(data) {
            alert("Your data could not be saved.")
            console.log(data)
        }
    })
}

export function AnimalRequest() {
     const [isSubmitting, setIsSubmitting] = useState(false);

     return (
         <form className="tw-w-full tw-max-w-4xl" onSubmit={handleSubmit} autoComplete="off">
            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                <Title text="1. Principal Investigator*"/>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="investigator-last-name" isSubmitting={isSubmitting} required={true} placeholder="Last Name"/>
                </div>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="investigator-first-name" isSubmitting={isSubmitting} required={true} placeholder="First Name"/>
                </div>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="investigator-middle-initial" isSubmitting={isSubmitting} required={true} placeholder="Middle Initial"/>
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
                    <YesNoRadio id="is-principal-investigator" required={true}/>
                </div>
            </div>

            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                <Title text="3. Affiliated research institution*"/>

                <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="institution-name" isSubmitting={isSubmitting} placeholder="Name" required={true}/>
                </div>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="institution-city" isSubmitting={isSubmitting} placeholder="City" required={true}/>
                </div>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="institution-state" isSubmitting={isSubmitting} placeholder="State" required={true}/>
                </div>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="institution-country" isSubmitting={isSubmitting} placeholder="Country" required={true}/>
                </div>
            </div>

            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                <Title text="4. Institution Type*"/>

                <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Select id="institution-type" isSubmitting={isSubmitting} options={institutionTypeOptions} />
                </div>
            </div>

            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                <Title text="5. Institution Signing Official*"/>

                <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="official-last-name" isSubmitting={isSubmitting} placeholder="Last Name" required={true}/>
                </div>

                <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="official-first-name" isSubmitting={isSubmitting} placeholder="First Name" required={true}/>
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="official-email" isSubmitting={isSubmitting} placeholder="Email Address" required={true}/>
                </div>
            </div>

            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                <Title text="6. Co-investigators"/>

                <CoInvestigators isSubmitting={isSubmitting}/>
            </div>

            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                <Title text="7. Existing or proposed funding source*"/>

                <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Select id="funding-source" isSubmitting={isSubmitting} options={fundingSourceOptions} required={true}/>
                </div>
            </div>
             
            <div className="tw-flex tw-flex-wrap tw-mx-2">
                <Title text="8. Research Use Statement*"/>

                <div className="tw-w-full tw-px-3 tw-mb-10">
                    <TextArea id="experiment-rationale" isSubmitting={isSubmitting} placeholder={experimentalRationalePlaceholder} required={true}/>
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-10">
                    <Title text="Number of animals needed:&nbsp;&nbsp;&nbsp;&nbsp;"/>
                    <InputNumber id="number-of-animals" isSubmitting={isSubmitting} required={true}/>
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-10">
                    <TextArea id="other-characteristics" isSubmitting={isSubmitting} placeholder={otherCharacteristicsPlaceholder} required={true}/>
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-10">
                    <TextArea id="methods-proposed" isSubmitting={isSubmitting} placeholder={methodsProposedPlaceholder} required={true}/>
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-10">
                    <TextArea id="collaborations" isSubmitting={isSubmitting} placeholder={collaborationsPlaceholder} required={true}/>
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-10">
                    <div className="tw-mb-6">
                        <Title text="Do you plan to breed animals?"/>
                    </div>

                    <div className="tw-mb-6">
                        <YesNoRadio id="is-planning-to-breed-animals" required={true}/>
                    </div>
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-10">
                    <TextArea id="of-interest-centers" isSubmitting={isSubmitting} placeholder={ofInterestCentersPlaceholder} required={true}/>
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-6">
                    <div className="tw-mb-6">
                        <Title text="Research Area"/>
                    </div>
                    <ResearchArea id="research-area" isSubmitting={isSubmitting} />
                </div>

                <div className="tw-w-full tw-px-3">
                    <div className="tw-mb-6">
                        <Title text="Animal Facilities and Capabilities"/>
                    </div>

                    <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                        <Title text="Does your institution have an existing marmoset colony?"/>
                        <Select id="existing-marmoset-colony" isSubmitting={isSubmitting} options={existingMarmosetColonyOptions} />
                    </div>

                    <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                        <Title text="Does your institution have existing NHP facilities?"/>
                        <Select id="existing-nhp-facilities" isSubmitting={isSubmitting} options={existingNHPFacilityOptions} />
                    </div>
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-6">
                    <div className="tw-w-full tw-px-3 tw-mb-6">
                        <TextArea id="animal-welfare" isSubmitting={isSubmitting} placeholder={animalWellfarePlaceholder} required={true}/>
                    </div>

                    <div className="tw-w-full tw-px-3 tw-mb-6">
                        <input type="checkbox" name="certify" required={true}/>
                        <label className="tw-text-gray-700 ml-1">{certificationLabel}</label>
                    </div>
                </div>

                <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                    <Title text="Attending veterinarian"/>

                    <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="vet-last-name" isSubmitting={isSubmitting} placeholder="Last Name" required={true}/>
                    </div>

                    <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="vet-first-name" isSubmitting={isSubmitting} placeholder="First Name" required={true}/>
                    </div>

                    <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="vet-email" isSubmitting={isSubmitting} placeholder="Email Address" required={true}/>
                    </div>
                </div>

                <div className="tw-flex tw-flex-wrap tw-mx-2">
                    <Title text="IACUC Approval"/>

                    <div className="tw-w-full tw-px-3 md:tw-mb-0">
                        <Select id="iacuc-approval" isSubmitting={isSubmitting} options={IACUCApprovalOptions} required={true}/>
                    </div>
                </div>
            </div>

            <div className="tw-flex tw-flex-wrap tw-mx-2">
                <button className="tw-ml-auto tw-bg-blue-500 hover:tw-bg-blue-400 tw-text-white tw-font-bold tw-py-4 tw-mt-2 tw-px-6 tw-border-none tw-rounded" onClick={() => setIsSubmitting(true)}>Submit</button>
            </div>
        </form>
     )
}
