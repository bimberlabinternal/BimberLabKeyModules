import React, { useEffect, useRef } from 'react';
import { BarController, BarElement, CategoryScale, Chart, Legend, LinearScale, Tooltip } from 'chart.js';
import { ActiveElement, ChartEvent } from 'chart.js/dist/types/index';

Chart.register(Legend, BarController, BarElement, CategoryScale, LinearScale, Tooltip);

const colors = [
    "#E41A1C",
    "#377EB8",
    "#4DAF4A",
    "#984EA3",
    "#FF7F00",
    "#FFFF33",
    "#A65628",
    "#F781BF",
    "#999999"
];

export enum ColorType {
    PRIMARY = "primary",
    GROUP = "group"
}

export default function BarChart(props: {demographics: [], fieldName: string, groupField?: string, colorBy?: ColorType, showLegend?: boolean, indexAxis?: 'x' | 'y', missingDataTerm?: string, onClick?: (event: ChartEvent, elements: ActiveElement[], chart: Chart) => void }) {
    const canvas = useRef(null);

    const { demographics, fieldName, groupField, onClick} = props
    const { colorBy = ColorType.PRIMARY } = props
    const { showLegend = false } = props
    const { missingDataTerm = 'None' } = props

    const indexAxis: 'x' | 'y' = props.indexAxis || 'y'

    const collectedData = demographics.reduce((acc, curr: {}, idx) => {
        const value = curr[fieldName] === null ? 'Unknown' : curr[fieldName];
        const group = groupField == null ? 'counts' : curr[groupField] || missingDataTerm

        if (!acc[group]) {
            acc[group] = {}
        }

        if (acc[group][value]) {
            acc[group][value] = acc[group][value] + 1;
        } else {
            acc[group][value] = 1;
        }

        return acc;
    }, {});

    const labels = [...new Set(Object.keys(collectedData).flatMap(groupName => Object.keys(collectedData[groupName])))].sort(Intl.Collator().compare)
    const groupNames = Object.keys(collectedData).sort(Intl.Collator().compare)

    const dataArr: any[] = groupNames.map(groupName => {
        const dat = labels.map(label => collectedData[groupName] ? collectedData[groupName][label] || 0 : 0)
        return {
            label: groupName,
            data: dat,
            backgroundColor: colorBy == ColorType.PRIMARY ? colors.slice(0, labels.length) : colors[groupNames.indexOf(groupName)]
        }
    });

    useEffect(() => {
        const chart = new Chart(canvas.current, {
            type: 'bar',
            data: {
                labels,
                datasets: dataArr
            },
            options: {
                onClick: onClick,
                responsive: true,
                aspectRatio: 2,
                indexAxis: indexAxis,
                scales: {
                    x: {
                        beginAtZero: true
                    }
                },
                plugins: {
                    legend: {
                        display: showLegend,
                        position: 'right'
                    }
                }
            }
        });
        return () => {
            chart.destroy();
        };
    }, [] /* only run the effect on mount */)

    return (
        <canvas ref={canvas}></canvas>
    );
}