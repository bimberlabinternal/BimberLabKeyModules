import React from 'react';

export default function Button(props: {onClick: (event: MouseEvent) => void, id?: string, display?: boolean, text: string, form?: string, baseColor?: string, marginLeft?: string }) {
    function onClick(e) {
        if (props.onClick) {
            props.onClick(e)
        }
    }

    const baseColor = props.baseColor ? props.baseColor : 'blue'
    const marginLeft = props.marginLeft ? props.marginLeft : '16'
    const classes = "tw-ml-" + marginLeft + " tw-bg-" + baseColor + "-500 hover:tw-bg-" + baseColor + "-400 tw-text-white tw-font-bold tw-py-4 tw-mt-2 tw-px-6 tw-border-none tw-rounded"

    if (props.display === false) {
        return (
            <>
            </>
        )
    } else {
        return (
            <button id={props.id}
                    className={classes}
                    onClick={(e) => onClick(e)}
                    form={props.form}>{props.text}
            </button>
        )
    }
}
