import React from 'react'

export default function SavingOverlay(props) {
    if(props.display === false) {
        return (
            <>
            </>
        )
    } else {
        return (
            <div className="tw-w-full tw-h-full tw-fixed tw-block tw-top-0 tw-left-0 tw-bg-white tw-opacity-75 tw-z-50 tw-flex tw-flex-col tw-items-center tw-justify-center">
                <div className="tw-flex tw-justify-center tw-items-center">
                     <div style={{ borderBottom: "2px solid #3495d2" }} className="tw-animate-spin tw-rounded-full tw-h-32 tw-w-32"></div>
                </div>
                <h2 className="text-center text-white text-xl font-semibold">Saving</h2>
            </div>
        )
    }
}
