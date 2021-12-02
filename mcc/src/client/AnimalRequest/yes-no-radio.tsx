import React from 'react'

export default function Radio(props) {
    function onChange(e) {
        if(props.onChange) {
            props.onChange(e)
        }
    }

    return (
        <div id={props.id} className="tw-flex tw-flex-wrap tw-mx-2">
            <div className="tw-w-full md:tw-w-1/6 tw-px-3 tw-mb-6 md:tw-mb-0">
                <label className="tw-text-gray-700 ml-1">
                    <input name={props.id} id={props.id + "-yes"} type="radio" value="yes" required={props.required}
                     defaultChecked={props.defaultValue === true} onChange={(e) => onChange(e)}/>
                    <p className="tw-inline">Yes</p>
                </label>
            </div>

            <div className="tw-w-full md:tw-w-1/6 tw-px-3 tw-mb-6 md:tw-mb-0">
                <label className="tw-text-gray-700 ml-1">
                    <input name={props.id} id={props.id + "-no"} type="radio" value="no" required={props.required}
                     defaultChecked={props.defaultValue === false} onChange={(e) => onChange(e)}/>
                    <p className="tw-inline">No</p>
                </label>
            </div>
        </div>
    )
}
