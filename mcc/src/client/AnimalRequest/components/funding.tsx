import React, { useState } from 'react';

import Select from './select'
import Input from './input'
import Title from './title'
import Date from './date'
import ErrorMessageHandler from './error-message-handler'

import { fundingSourceOptions } from './values'

export default function Funding(props) {

    const [displayApplicationDate, setDisplayApplicationDate] = useState(props.defaultValue.fundingsource == "no-funding" ? true : false)

    function setDisplayApplicationDateField(value) {
        if(value == "no-funding") {
            setDisplayApplicationDate(true)
        } else {
            setDisplayApplicationDate(false)
        }
    }

    return (
        <ErrorMessageHandler isSubmitting={props.isSubmitting} rerender={displayApplicationDate}>
        <div className="tw-w-full tw-mx-2 tw-mb-4">
            <div className="tw-w-full tw-px-3 tw-mb-6">
                <Select id={props.id + "-source"} ariaLabel="Proposed Funding Source(s)" isSubmitting={props.isSubmitting} options={fundingSourceOptions} onChange={(e) => setDisplayApplicationDateField(e.currentTarget.value)} multiSelect={true} required={props.required} defaultValue={props.defaultValue.fundingsource} />
            </div>

            <div className="tw-w-full tw-px-3">
                <Input id={props.id + "-grant-number"} ariaLabel="Grant Number" isSubmitting={props.isSubmitting} placeholder="Grant Number(s)" display={!displayApplicationDate} required={!displayApplicationDate && props.required} defaultValue={props.defaultValue.grantnumber}/>
                <Title text="Application Date" display={displayApplicationDate}/>
                <Date id={props.id + "-application-due-date"} ariaLabel="Application Date" isSubmitting={props.isSubmitting} placeholder="Application due date" display={displayApplicationDate} required={displayApplicationDate && props.required} defaultValue={props.defaultValue.applicationduedate}/>
            </div>
        </div>
        </ErrorMessageHandler>
    )
}
