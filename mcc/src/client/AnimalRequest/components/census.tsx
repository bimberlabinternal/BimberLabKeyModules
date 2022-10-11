import React, { useState } from 'react';

import YesNoRadio from './yes-no-radio';
import TextArea from './text-area';
import ErrorMessageHandler from './error-message-handler';

import { censusReasonPlaceholder } from './values';
import { AnimalRequestProps } from '../../components/RequestUtils';

export default function AnimalCensus(props: {request: AnimalRequestProps, isSubmitting: boolean, required: boolean, id: string}) {

    const [displayReason, setDisplayReason] = useState(props.request.census === false)

    function setDisplayReasonField(value) {
        if (value === "no") {
            setDisplayReason(true)
        } else {
            setDisplayReason(false)
        }
    }

    return (
        <ErrorMessageHandler isSubmitting={props.isSubmitting}>
            <div className="tw-mb-6">
                <YesNoRadio id={props.id + "-participate-in-census"} ariaLabel="Will participate in MCC Census" isSubmitting={props.isSubmitting} required={props.required} defaultValue={props.request.census}
                            onChange={(e) => setDisplayReasonField(e.currentTarget.value)}/>
            </div>

            <div className="tw-mb-6">
                <TextArea id={props.id + "-reason"} ariaLabel="Reason for not participating" isSubmitting={props.isSubmitting} placeholder={censusReasonPlaceholder}
                          display={displayReason} required={displayReason && props.required} defaultValue={props.request.censusreason}/>
            </div>
        </ErrorMessageHandler>
    )
}
