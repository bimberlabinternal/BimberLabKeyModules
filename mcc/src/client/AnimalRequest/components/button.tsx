import React from 'react'

export default function Button(props) {
    function onClick() {
        if(props.onClick) {
            props.onClick()
        }
    }
    if(props.display === false) {
        return (
            <>
            </>
        )
    } else {
        return (
            <button className="tw-ml-16 tw-bg-blue-500 hover:tw-bg-blue-400 tw-text-white tw-font-bold tw-py-4 tw-mt-2 tw-px-6 tw-border-none tw-rounded"
             onClick={() => onClick()}>{props.text}</button>
        )
    }
}
