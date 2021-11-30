import React from 'react'

import Title from './title'

export default function TextArea(props) {
    return (
        <div className="tw-flex tw-flex-wrap tw-mx-2">
            <Title text={props.placeholder}/>
            <textarea className={"tw-w-full tw-h-36 tw-px-3 tw-py-2 tw-text-base" + (props.isSubmitting && " tw-invalid ") + "tw-text-gray-700 tw-placeholder-gray-600 tw-border tw-rounded-lg focus:tw-shadow-outline"}
                name={props.id} id={props.id} placeholder={props.placeholder} required={props.required} defaultValue={props.defaultValue}/>
        </div>
    )
}
