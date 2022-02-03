import React, { useState } from 'react';

export default function Select(props) {
    const [value, setValue] = useState("none")

    function onChange(e: any) {
        setValue(e.currentTarget.value)

        if (props.onChange) {
            props.onChange(e)
        }
    }

    return (
        <select className={"tw-block tw-border tw-border-gray-300" + (props.isSubmitting && " tw-invalid ") + "tw-text-gray-700 tw-py-3 tw-pr-8 tw-rounded tw-leading-tight focus:tw-outline-none"}
          name={props.id} aria-label={props.ariaLabel} id={props.id} required={props.required} defaultValue={props.defaultValue || ""} onChange={(e) => onChange(e)}>

            <option disabled value=""> -- </option>
            {props.options.map(({ value, label }, _) => <option key={value} value={value}>{label}</option>)}
        </select>
    )
}
