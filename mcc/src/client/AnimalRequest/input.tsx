import React from 'react';

export default function Input(props) {
    return (
        <input className="tw-appearance-none tw-block tw-w-full tw-text-gray-700 tw-border-gray-300 tw-rounded tw-py-4 tw-px-6 tw-mb-3 tw-leading-tight focus:tw-outline-none" 
         id={props.id} type="text" placeholder={props.placeholder} onChange={props.onChange} value={props.value}/>
    )
}