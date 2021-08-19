import React, { useState } from 'react';

export default function Select(props) {
    const [value, setValue] = useState("none")

    return (
        <select className={"tw-block tw-border tw-border-gray-300" + (props.isSubmitting && value === "none" && " tw-border-red-500 ") + "tw-text-gray-700 tw-py-3 tw-pr-8 tw-rounded tw-leading-tight focus:tw-outline-none"} defaultValue="none"
            name={props.id} id={props.id} required={props.required} onChange={(e) => setValue(e.currentTarget.value)}>

            <option disabled value="none"> -- </option>
            {props.options.map(({ value, label }, _) => <option key={value} value={value}>{label}</option>)}
        </select>
    )
}
