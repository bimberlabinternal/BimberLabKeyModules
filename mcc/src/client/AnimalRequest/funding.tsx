import React, { useState } from 'react';

import Select from './select'
import Input from './input'
import Title from './title'
import Date from './date'

import { fundingSourceOptions } from './values'

export default function Funding(props) {

    const [displayApplicationDate, setDisplayApplicationDate] = useState(props.defaultValue.fundingsource == "no-funding" ? true : false);

    function setDisplayApplicationDateField(value) {
        if(value == "no-funding") {
            setDisplayApplicationDate(true)
        } else {
            setDisplayApplicationDate(false)
        }
    }

    return (
        <>
        <div className="tw-w-full tw-px-3 tw-mb-6">
            <Select id={props.id + "-source"} isSubmitting={props.isSubmitting} options={fundingSourceOptions} onChange={(e) => setDisplayApplicationDateField(e.currentTarget.value)} required={true} defaultValue={props.defaultValue.fundingsource} />
        </div>

        <div className="tw-w-full tw-px-3">
            <Input id={props.id + "-grant-number"} isSubmitting={props.isSubmitting} placeholder="Grant Number(s)" display={!displayApplicationDate} required={!displayApplicationDate} defaultValue={props.defaultValue.grantnumber}/>
            <Title text="Application Date" display={displayApplicationDate}/>
            <Date id={props.id + "-application-due-date"} isSubmitting={props.isSubmitting} placeholder="Application due date" display={displayApplicationDate} required={displayApplicationDate} defaultValue={props.defaultValue.applicationduedate}/>
        </div>
        </>
    )
}
