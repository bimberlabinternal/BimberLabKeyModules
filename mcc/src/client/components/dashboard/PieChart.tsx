import React, { useEffect, useRef } from 'react';
import { ArcElement, Chart, Legend, PieController, Tooltip } from 'chart.js';

Chart.register(ArcElement, Legend, PieController, Tooltip);

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

export default function PieChart(props: {demographics: [], fieldName: string, cutout?: string, collapseBelow?: number }) {
    const canvas = useRef(null);

    const { demographics, fieldName, cutout = '0', collapseBelow = 0 } = props;

    const collectedData  = demographics.reduce((acc, curr) => {
        const value = curr[fieldName] === null ? 'Unknown' : curr[fieldName];
        if (acc[value]) {
            acc[value] = acc[value] + 1;
        } else {
            acc[value] = 1;
        }

        return acc;
    }, new Map<string, bigint>())

    if (collapseBelow) {
        const total = Object.keys(collectedData).reduce((sum, keyName) => {
            sum += collectedData[keyName]

            return sum
        }, 0)

        const otherValue = Object.keys(collectedData).reduce((sum, keyName) => {
            const val = collectedData[keyName]
            const fraction = val / total
            if (fraction < collapseBelow) {
                delete collectedData[keyName]
                sum += val
            }

            return sum
        }, 0)

        if (otherValue) {
            collectedData['Other'] = otherValue
        }
    }

    const labels = Object.keys(collectedData).sort(Intl.Collator().compare);
    const data = labels.map(label => collectedData[label]);

    useEffect(() => {
        const chart = new Chart(canvas.current, {
            type: 'pie',
            data: {
                labels,
                datasets: [{
                    label: 'count',
                    data,
                    backgroundColor: colors.slice(0, labels.length),
                    hoverOffset: 4
                }]
            },
            options: {
                responsive: true,
                aspectRatio: 2,
                cutout: cutout,
                plugins: {
                    legend: {
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