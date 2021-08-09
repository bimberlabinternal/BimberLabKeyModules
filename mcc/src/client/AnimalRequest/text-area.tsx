import React from 'react'

export default function TextArea(props) {
    return (
        <textarea className="tw-w-full tw-h-36 tw-px-3 tw-py-2 tw-text-base tw-text-gray-700 tw-placeholder-gray-600 tw-border tw-rounded-lg focus:tw-shadow-outline" id={props.id} placeholder={props.placeholder}></textarea>
    )
}
