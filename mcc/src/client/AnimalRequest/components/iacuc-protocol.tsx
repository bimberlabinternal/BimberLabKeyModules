import React, { useState } from 'react';

import Select from './select'
import Input from './input'
import Title from './title'
import ErrorMessageHandler from './error-message-handler'

import { IACUCApprovalOptions } from './values'
import { AnimalRequestProps } from '../../components/RequestUtils';

export default function IACUCProtocol(props: {request: AnimalRequestProps, isSubmitting: boolean, required: boolean, id: string}) {

    const [displayProtocol, setDisplayProtocol] = useState(props.request.iacucapproval === "approved")

    function setDisplayProtocolField(value) {
        if (value == "approved") {
            setDisplayProtocol(true)
        } else {
            setDisplayProtocol(false)
        }
    }

    return (
        <ErrorMessageHandler isSubmitting={props.isSubmitting} rerender={displayProtocol}>
        <div className="tw-flex tw-flex-wrap tw-mx-2">
            <Title text="7. IACUC Approval"/>

            <div className="tw-w-full tw-px-3 md:tw-mb-0">

                <div className="tw-w-full tw-mb-6">
                    <Select id={props.id + "-approval"} ariaLabel="IACUC Approval Status" isSubmitting={props.isSubmitting} options={IACUCApprovalOptions} required={props.required}
                        defaultValue={props.request.iacucapproval} onChange={(e) => setDisplayProtocolField(e.currentTarget.value)}/>
                </div>

                <div className="tw-mb-6">
                    <Input id={props.id + "-protocol"} ariaLabel="IACUC Protocol" isSubmitting={props.isSubmitting} placeholder={"IACUC Protocol Number"}
                        display={displayProtocol} required={displayProtocol && props.required} defaultValue={props.request.iacucprotocol}/>
                </div>
            </div>
        </div>
        </ErrorMessageHandler>
    )
}
