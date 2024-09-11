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

export default function BarChart(props: {demographics: [], fieldName: string, groupField?: string, indexAxis?: 'x' | 'y', onClick?: (event: ChartEvent, elements: ActiveElement[], chart: Chart) => void }) {
    const canvas = useRef(null);

    const { demographics, fieldName, groupField, onClick} = props
    const indexAxis: 'x' | 'y' = props.indexAxis || 'y'

    const collectedData = demographics.reduce((acc, curr: {}, idx) => {
        const value = curr[fieldName] === null ? 'Unknown' : curr[fieldName];
        const group = groupField == null ? 'counts' : curr[groupField] || 'None'

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

    const labels = [...new Set(Object.keys(collectedData).flatMap(groupName => Object.keys(collectedData[groupName])))];
    const dataArr: any[] = Object.keys(collectedData).map(groupName => {
        const dat = labels.map(label => collectedData[groupName] ? collectedData[groupName][label] || 0 : 0)
        return {
            label: groupName,
            data: dat,
            backgroundColor: colors.slice(0, labels.length)
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
                        display: false
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