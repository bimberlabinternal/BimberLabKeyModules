import React, { useState } from 'react';

import Select from './components/select'
import Input from './components/input'
import Title from './components/title'
import ErrorMessageHandler from './components/error-message-handler'

import { researchAreaOptions } from './components/values'

export default function ResearchArea(props) {

    const [displayOther, setDisplayOther] = useState(props.defaultValue.researcharea == "other" ? true : false)

    function setDisplayOtherField(el) {
        const valArray = Array.prototype.map.call(el.selectedOptions, function(x){ return x.value })
        if (valArray && valArray.indexOf("other") > -1) {
            setDisplayOther(true)
        } else {
            setDisplayOther(false)
        }
    }

    return (
        <ErrorMessageHandler isSubmitting={props.isSubmitting} rerender={displayOther}>
        <div className="tw-w-full tw-px-3 tw-mb-10">
            <div className="tw-w-full tw-mb-8">
                <Select id={props.id} options={researchAreaOptions} ariaLabel="Research Area" multiSelect={true} isSubmitting={props.isSubmitting} onChange={(e) => setDisplayOtherField(e.currentTarget)} defaultValue={props.defaultValue.researcharea} required={props.required}/>
            </div>

            <div className="tw-w-full tw-mb-8">
                <Input id={props.id + "-other-specify"} ariaLabel="Other" isSubmitting={props.isSubmitting} placeholder="Please specify" display={displayOther} required={displayOther && props.required} defaultValue={props.defaultValue.otherjustification}/>
            </div>
        </div>
        </ErrorMessageHandler>
    )
}
