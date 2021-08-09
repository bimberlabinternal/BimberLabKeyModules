import React, { useState } from 'react';

import Select from './select'
import Input from './input'

import { researchAreaOptions } from './values'

export default function ResearchArea(props) {

    const [displayOther, setDisplayOther] = useState(false);

    function setDisplayOtherField(value) {
        console.log(value)
        console.log(displayOther)
        if(value == "other") {
            setDisplayOther(true)
        } else {
            setDisplayOther(false)
        }
    }

    return (
        <>
            <div className="tw-w-full tw-px-3 tw-mb-6">
                <Select id={props.id} options={researchAreaOptions} onChange={(e) => setDisplayOtherField(e.currentTarget.value)}/>
            </div>

            <div className="tw-w-full tw-px-3 tw-mb-6">
                <Input id="other-specify" placeholder="Please specify" display={displayOther}/>
            </div>
        </>
    )
}
