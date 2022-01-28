import React, { useState } from 'react';

import Select from './select'
import Input from './input'
import Title from './title'
import ErrorMessageHandler from './error-message-handler'

import { researchAreaOptions } from './values'

export default function ResearchArea(props) {

    const [displayOther, setDisplayOther] = useState(props.defaultValue.researcharea == "other" ? true : false)

    function setDisplayOtherField(value) {
        if(value == "other") {
            setDisplayOther(true)
        } else {
            setDisplayOther(false)
        }
    }

    return (
        <ErrorMessageHandler isSubmitting={props.isSubmitting} rerender={displayOther}>
        <div className="tw-w-full tw-px-3 tw-mb-10">
            <div className="tw-mb-6">
                <Title text="Research Area"/>
            </div>
            <div className="tw-w-full tw-mb-6">
                <Select id={props.id} options={researchAreaOptions} name="Research Area" isSubmitting={props.isSubmitting} onChange={(e) => setDisplayOtherField(e.currentTarget.value)} defaultValue={props.defaultValue.researcharea} required={props.required}/>
            </div>

            <div className="tw-w-full tw-mb-6">
                <Input id={props.id + "-other-specify"} name="Other" isSubmitting={props.isSubmitting} placeholder="Please specify" display={displayOther} required={displayOther && props.required} defaultValue={props.defaultValue.otherjustification}/>
            </div>
        </div>
        </ErrorMessageHandler>
    )
}
