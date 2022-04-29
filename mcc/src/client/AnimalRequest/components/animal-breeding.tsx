import React, { useState } from 'react';

import YesNoRadio from './yes-no-radio'
import TextArea from './text-area'
import ErrorMessageHandler from './error-message-handler'

import { animalBreedingPlaceholder } from './values'
import { AnimalRequestProps } from '../../components/RequestUtils';

export default function AnimalBreeding(props: {request: AnimalRequestProps, isSubmitting: boolean, required: boolean, id: string}) {

    const [displayPurpose, setDisplayPurpose] = useState(props.request.isbreedinganimals === true)

    function setDisplayPurposeField(value) {
        if(value == "yes") {
            setDisplayPurpose(true)
        } else {
            setDisplayPurpose(false)
        }
    }

    return (
        <ErrorMessageHandler isSubmitting={props.isSubmitting}>
            <div className="tw-mb-6">
                <YesNoRadio id={props.id + "-is-planning-to-breed-animals"} ariaLabel="Planning to breed animals" isSubmitting={props.isSubmitting} required={props.required} defaultValue={props.request.isbreedinganimals}
                 onChange={(e) => setDisplayPurposeField(e.currentTarget.value)}/>
            </div>

            <div className="tw-mb-6">
                <TextArea id={props.id + "-purpose"} ariaLabel="Breeding Program Purpose" isSubmitting={props.isSubmitting} placeholder={animalBreedingPlaceholder}
                    display={displayPurpose} required={displayPurpose && props.required} defaultValue={props.request.breedingpurpose}/>
            </div>
        </ErrorMessageHandler>
    )
}
