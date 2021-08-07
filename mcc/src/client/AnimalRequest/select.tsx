import React from 'react';

export default function Select(props) {
    return (
        <select defaultValue="none" className="tw-block tw-border tw-border-gray-300 tw-text-gray-700 tw-py-3 tw-pr-8 tw-rounded tw-leading-tight focus:tw-outline-none" id={props.id}>
            <option disabled value="none"> -- </option>
            {props.options.map(({ value, label }, _) => <option key={value} value={value} >{label}</option>)}
        </select>
    )
}