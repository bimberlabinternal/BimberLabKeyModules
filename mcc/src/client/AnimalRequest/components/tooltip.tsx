import React from 'react';
import { Tooltip as ReactToolTip } from 'react-tooltip';

export default function Tooltip(props) {
    return (
        <>
            <svg data-tooltip-html={props.text} data-tooltip-id={props.id} xmlns="http://www.w3.org/2000/svg" className="tw-h-6 tw-w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" tabIndex={-1}>
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>

            <ReactToolTip className="tw-w-96" style={{maxWidth: 350}} id={props.id} />
        </>
    )
}
