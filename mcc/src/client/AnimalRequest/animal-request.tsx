import React, { useState, useEffect } from 'react';
import { Query } from '@labkey/api';

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



export function AnimalRequest() {
     return (
        <>
        <form className="tw-w-full tw-max-w-4xl">
            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                <Title text="1. Principal Investigator*"/>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="investigator-last-name" placeholder="Last Name"/>
                </div>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="investigator-first-name" placeholder="First Name"/>
                </div>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="investigator-middle-initial" placeholder="Middle Initial"/>
                </div>
            </div>

            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                <div className="tw-relative tw-w-full tw-mb-6 md:tw-mb-0">
                    <Title text="2. Are you an early-stage investigator?&nbsp;"/>
                    <Tooltip id="early-stage-investigator-helper"
                       text={earlyInvestigatorTooltip}
                    />
                </div>


                <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0 tw-mt-6">
                    <YesNoRadio id="is-principal-investigator" />
                </div>
            </div>

            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                <Title text="3. Affiliated research institution*"/>

                <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="institution-name" placeholder="Name"/>
                </div>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="institution-city" placeholder="City"/>
                </div>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="institution-state" placeholder="State"/>
                </div>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="institution-country" placeholder="Country"/>
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
                    <Input id="official-last-name" placeholder="Last Name"/>
                </div>

                <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="official-first-name" placeholder="First Name"/>
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Input id="official-email" placeholder="Email Address"/>
                </div>
            </div>

            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                <Title text="6. Co-investigators"/>

                <CoInvestigators />
            </div>

            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                <Title text="7. Existing or proposed funding source*"/>

                <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Select id="funding-source" options={fundingSourceOptions} />
                </div>
            </div>
             
            <div className="tw-flex tw-flex-wrap tw-mx-2">
                <Title text="8. Research Use Statement*"/>

                <div className="tw-w-full tw-px-3 tw-mb-6">
                    <TextArea id="experiment-rationale" placeholder={experimentalRationalePlaceholder}/>
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-6">
                    <Title text="Number of animals needed:&nbsp;&nbsp;&nbsp;&nbsp;"/>
                    <InputNumber id="number-of-animals"/>
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-6">
                    <TextArea id="other-characteristics" placeholder={otherCharacteristicsPlaceholder}/>
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-6">
                    <TextArea id="methods-proposed" placeholder={methodsProposedPlaceholder}/>
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-6">
                    <TextArea id="collaborations" placeholder={collaborationsPlaceholder}/>
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0 tw-mt-6">
                    <div className="tw-mb-6">
                        <Title text="Do you plan to breed animals?"/>
                    </div>

                    <YesNoRadio id="is-planning-to-breed-animals" />
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-6">
                    <TextArea id="of-interest-centers" placeholder={ofInterestCentersPlaceholder}/>
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
                            <input type="radio" name="existing-marmoset-colony" value="existing"/>
                            <label className="tw-text-gray-700 ml-1">Existing marmoset colony</label>
                        </div>

                        <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                            <input type="radio" name="existing-marmoset-colony" value="not-existing"/>
                            <label className="tw-text-gray-700 ml-1">No existing marmoset colony</label>
                        </div>
                    </div>

                    <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                        <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                            <input type="radio" name="existing-nhp-facilities" value="existing"/>
                            <label className="tw-text-gray-700 ml-1">Existing NHP facilities</label>
                        </div>

                        <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                            <input type="radio" name="existing-nhp-facilities" value="not-existing"/>
                            <label className="tw-text-gray-700 ml-1">No existing NHP facilities</label>
                        </div>
                    </div>
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-6">
                    <div className="tw-w-full tw-px-3 tw-mb-6">
                        <TextArea id="animal-welfare" placeholder={animalWellfarePlaceholder}/>
                    </div>

                    <div className="tw-w-full tw-px-3 tw-mb-6">
                        <input type="checkbox" name="certify"/>
                        <label className="tw-text-gray-700 ml-1">{certificationLabel}</label>
                    </div>
                </div>

                <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                    <Title text="Attending veterinarian"/>

                    <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="vet-last-name" placeholder="Last Name"/>
                    </div>

                    <div className="tw-w-full md:tw-w-1/2 tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="vet-first-name" placeholder="First Name"/>
                    </div>

                    <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                        <Input id="vet-email" placeholder="Email Address"/>
                    </div>
                </div>

                <div className="tw-flex tw-flex-wrap tw-mx-2">
                    <Title text="IACUC Approval"/>

                    <div className="tw-w-full tw-px-3 md:tw-mb-0">
                        <Select id="iacuc-approval" options={IACUCApprovalOptions} />
                    </div>
                </div>
            </div>

            <div className="tw-flex tw-flex-wrap tw-mx-2">
                <button className="tw-ml-auto tw-bg-blue-500 hover:tw-bg-blue-400 tw-text-white tw-font-bold tw-py-4 tw-mt-2 tw-px-6 tw-border-none tw-rounded">Submit</button>
            </div>
        </form>
        </>
     )
}
