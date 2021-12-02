import React, { useState } from 'react';

import Select from './select'
import Input from './input'

import { IACUCApprovalOptions } from './values'

export default function IACUCProtocol(props) {

    const [displayProtocol, setDisplayProtocol] = useState(props.defaultValue.iacucapproval == "approved" ? true : false)

    function setDisplayProtocolField(value) {
        if(value == "approved") {
            setDisplayProtocol(true)
        } else {
            setDisplayProtocol(false)
        }
    }

    return (
        <>
            <div className="tw-w-full tw-mb-6">
                <Select id={props.id + "-approval"} isSubmitting={props.isSubmitting} options={IACUCApprovalOptions} required={props.required}
                 defaultValue={props.defaultValue.iacucapproval} onChange={(e) => setDisplayProtocolField(e.currentTarget.value)}/>
            </div>

            <div className="tw-mb-6">
                <Input id={props.id + "-protocol"} isSubmitting={props.isSubmitting} placeholder={"IACUC Protocol Number"}
                    display={displayProtocol} required={displayProtocol && props.required} defaultValue={props.defaultValue.iacucprotocol}/>
            </div>
        </>
    )
}
