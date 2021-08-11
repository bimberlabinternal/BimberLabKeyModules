import React from 'react'

export default function YesNoRadio(props) {
    return (
        <div id={props.id} className="tw-flex tw-flex-wrap tw-mx-2">
            <div className="tw-w-full md:tw-w-1/6 tw-px-3 tw-mb-6 md:tw-mb-0">
                <input id={props.id + "-yes"} type="radio" name={props.id} value="yes"/>
                <label className="tw-text-gray-700 ml-1" htmlFor={props.id + "-yes"}>Yes</label>
            </div>

            <div className="tw-w-full md:tw-w-1/6 tw-px-3 tw-mb-6 md:tw-mb-0">
                <input id={props.id + "-no"} type="radio" name={props.id} value="no"/>
                <label className="tw-text-gray-700 ml-1" htmlFor={props.id + "-yes"}>No</label>
            </div>
        </div>
    )
}
