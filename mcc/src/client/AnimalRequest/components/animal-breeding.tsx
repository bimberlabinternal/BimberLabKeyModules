import React, { useState } from 'react';

import YesNoRadio from './yes-no-radio'
import TextArea from './text-area'
import ErrorMessageHandler from './error-message-handler'

import { animalBreedingPlaceholder, breedingOptions } from './values';
import { AnimalRequestProps } from '../../components/RequestUtils';
import Select from './select';

export default function AnimalBreeding(props: {request: AnimalRequestProps, isSubmitting: boolean, required: boolean, id: string}) {

    const [displayPurpose, setDisplayPurpose] = useState(['Request breeding pair', 'Will pair with existing animals'].includes(props.request.breedinganimals))

    //NIH expects requestors to participate in the MCC census, including information sharing and tissue sharing for genome sequencing

    function setDisplayPurposeField(value) {
        if(value && "Will not breed" !== value) {
            setDisplayPurpose(true)
        } else {
            setDisplayPurpose(false)
        }
    }

    return (
        <ErrorMessageHandler isSubmitting={props.isSubmitting}>
            <div className="tw-mb-6">
                <Select id={props.id + "-is-planning-to-breed-animals"} ariaLabel="Planning to breed animals" isSubmitting={props.isSubmitting} options={breedingOptions} required={props.required} defaultValue={props.request.breedingpurpose}
                        onChange={(e) => setDisplayPurposeField(e.currentTarget.value)}/>
            </div>

            <div className="tw-mb-6">
                <TextArea id={props.id + "-purpose"} ariaLabel="Breeding Program Purpose" isSubmitting={props.isSubmitting} placeholder={animalBreedingPlaceholder}
                    display={displayPurpose} required={displayPurpose && props.required} defaultValue={props.request.breedingpurpose}/>
            </div>
        </ErrorMessageHandler>
    )
}
