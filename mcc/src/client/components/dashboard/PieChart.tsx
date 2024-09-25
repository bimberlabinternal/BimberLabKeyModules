import React, { useEffect, useRef } from 'react';
import {
    Chart,
    ArcElement,
    Legend,
    PieController,
    Tooltip
} from 'chart.js';

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

export default function PieChart(props) {
    const canvas = useRef(null);

    const { demographics } = props;
    const { fieldName } = props;
    const { cutout } = props || 0;

    const collectedData = demographics.reduce((acc, curr) => {
        const value = curr[fieldName] === null ? 'Unknown' : curr[fieldName];
        if (acc[value]) {
            acc[value] = acc[value] + 1;
        } else {
            acc[value] = 1;
        }

        return acc;
    }, {});
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