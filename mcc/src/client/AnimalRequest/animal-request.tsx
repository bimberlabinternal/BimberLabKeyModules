import React, { useState, useEffect } from 'react';
import { Query } from '@labkey/api';

import Tooltip from './tooltip'
import Title from './title'
import Input from './input'
import Select from './select'
import CoInvestigators from './co-investigators'

import {
    earlyInvestigatorTooltip, institutionTypeOptions, 
    isPrincipalInvestigatorOptions, fundingSourceOptions,
    researchAreaOptions
} from './values'


export function AnimalRequest() {
     return (
        <>
        <form className="tw-w-full tw-max-w-4xl">
            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                <Title text="Principal Investigator"/>

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
                    <Title text="Are you an early-stage investigator?&nbsp;"/>
                    <Tooltip id="early-stage-investigator-helper"
                       text={earlyInvestigatorTooltip}
                    />
                </div>


                <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0 tw-mt-6">
                    <Select options={isPrincipalInvestigatorOptions} />
                </div>
            </div>

            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                <Title text="Affiliated research institution"/>

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
                <Title text="Institution Type"/>

                <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Select options={institutionTypeOptions} />
                </div>
            </div>

            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                <Title text="Institution Signing Official"/>

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
                <Title text="Co-investigators"/>

                <CoInvestigators />
            </div>

            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                <Title text="Existing or proposed funding source"/>

                <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                    <Select options={fundingSourceOptions} />
                </div>
            </div>
             
            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                <Title text="Research Use Statement"/>
            </div>

            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                <Title text="Research Area"/>

                <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                    {/*<Select options={research_area_options} />*/}
                </div>
            </div>

            <div className="tw-flex tw-flex-wrap tw-mx-2 tw-mb-10">
                <Title text="Animal Facilities and Capabilities"/>
            </div>
        </form>
        </>
     )
}