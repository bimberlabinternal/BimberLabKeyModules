import React from 'react'

import Title from './title'

export default function TextArea(props) {
    if(props.display === false) {
        return (
            <>
            </>
        )
    } else {
        return (
            <div className="tw-flex tw-flex-wrap">
                <Title text={props.placeholder}/>
                <textarea className={"tw-w-full tw-h-36 tw-px-3 tw-py-2 tw-text-base" + (props.isSubmitting && " tw-invalid ") + "tw-text-gray-700 tw-placeholder-gray-600 tw-border tw-rounded-lg focus:tw-shadow-outline"}
                    name={props.id} aria-label={props.ariaLabel} id={props.id} placeholder={props.placeholder} required={props.required} defaultValue={props.defaultValue}/>
            </div>
        )
    }
}
