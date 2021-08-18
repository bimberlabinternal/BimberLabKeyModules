import React, { useState, useEffect, FormEvent } from 'react';
import { Query } from '@labkey/api';
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
    certificationLabel, IACUCApprovalOptions
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
     return (
        <form className="tw-w-full tw-max-w-4xl" onSubmit={handleSubmit}>
            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                <Title text="1. Principal Investigator*"/>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="investigator-last-name" required={true} placeholder="Last Name"/>
                </div>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="investigator-first-name" required={true} placeholder="First Name"/>
                </div>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="investigator-middle-initial" required={true} placeholder="Middle Initial"/>
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
                    <Input id="institution-name" placeholder="Name" required={true}/>
                </div>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="institution-city" placeholder="City" required={true}/>
                </div>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="institution-state" placeholder="State" required={true}/>
                </div>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="institution-country" placeholder="Country" required={true}/>
                </div>
            </div>

            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                <Title text="4. Institution Type*"/>

                <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Select id="institution-type" options={institutionTypeOptions} />
                </div>
            </div>

            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                <Title text="5. Institution Signing Official*"/>

                <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="official-last-name" placeholder="Last Name" required={true}/>
                </div>

                <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="official-first-name" placeholder="First Name" required={true}/>
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="official-email" placeholder="Email Address" required={true}/>
                </div>
            </div>

            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                <Title text="6. Co-investigators"/>

                <CoInvestigators />
            </div>

            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                <Title text="7. Existing or proposed funding source*"/>

                <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Select id="funding-source" options={fundingSourceOptions} required={true}/>
                </div>
            </div>
             
            <div className="tw-flex tw-flex-wrap tw-mx-2">
                <Title text="8. Research Use Statement*"/>

                <div className="tw-w-full tw-px-3 tw-mb-6">
                    <TextArea id="experiment-rationale" placeholder={experimentalRationalePlaceholder} required={true}/>
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-6">
                    <Title text="Number of animals needed:&nbsp;&nbsp;&nbsp;&nbsp;"/>
                    <InputNumber id="number-of-animals" required={true}/>
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-6">
                    <TextArea id="other-characteristics" placeholder={otherCharacteristicsPlaceholder} required={true}/>
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-6">
                    <TextArea id="methods-proposed" placeholder={methodsProposedPlaceholder} required={true}/>
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-6">
                    <TextArea id="collaborations" placeholder={collaborationsPlaceholder} required={true}/>
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-6 tw-mt-6">
                    <div className="tw-mb-6">
                        <Title text="Do you plan to breed animals?"/>
                    </div>

                    <div className="tw-mb-6">
                        <YesNoRadio id="is-planning-to-breed-animals" required={true}/>
                    </div>
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-6">
                    <TextArea id="of-interest-centers" placeholder={ofInterestCentersPlaceholder} required={true}/>
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-6">
                    <div className="tw-mb-6">
                        <Title text="Research Area"/>
                    </div>
                    <ResearchArea id="research-area" />
                </div>

                <div className="tw-w-full tw-px-3">
                    <div className="tw-mb-6">
                        <Title text="Animal Facilities and Capabilities"/>
                    </div>

                    <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                        <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                            <input type="radio" name="existing-marmoset-colony" value="existing" required/>
                            <label className="tw-text-gray-700 ml-1">Existing marmoset colony</label>
                        </div>

                        <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                            <input type="radio" name="existing-marmoset-colony" value="not-existing" required/>
                            <label className="tw-text-gray-700 ml-1">No existing marmoset colony</label>
                        </div>
                    </div>

                    <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                        <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                            <input type="radio" name="existing-nhp-facilities" value="existing" required/>
                            <label className="tw-text-gray-700 ml-1">Existing NHP facilities</label>
                        </div>

                        <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                            <input type="radio" name="existing-nhp-facilities" value="not-existing" required/>
                            <label className="tw-text-gray-700 ml-1">No existing NHP facilities</label>
                        </div>
                    </div>
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-6">
                    <div className="tw-w-full tw-px-3 tw-mb-6">
                        <TextArea id="animal-welfare" placeholder={animalWellfarePlaceholder} required={true}/>
                    </div>

                    <div className="tw-w-full tw-px-3 tw-mb-6">
                        <input type="checkbox" name="certify" required={true}/>
                        <label className="tw-text-gray-700 ml-1">{certificationLabel}</label>
                    </div>
                </div>

                <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                    <Title text="Attending veterinarian"/>

                    <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="vet-last-name" placeholder="Last Name" required={true}/>
                    </div>

                    <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="vet-first-name" placeholder="First Name" required={true}/>
                    </div>

                    <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="vet-email" placeholder="Email Address" required={true}/>
                    </div>
                </div>

                <div className="tw-flex tw-flex-wrap tw-mx-2">
                    <Title text="IACUC Approval"/>

                    <div className="tw-w-full tw-px-3 md:tw-mb-0">
                        <Select id="iacuc-approval" options={IACUCApprovalOptions} required={true}/>
                    </div>
                </div>
            </div>

            <div className="tw-flex tw-flex-wrap tw-mx-2">
                <button className="tw-ml-auto tw-bg-blue-500 hover:tw-bg-blue-400 tw-text-white tw-font-bold tw-py-4 tw-mt-2 tw-px-6 tw-border-none tw-rounded">Submit</button>
            </div>
        </form>
     )
}
