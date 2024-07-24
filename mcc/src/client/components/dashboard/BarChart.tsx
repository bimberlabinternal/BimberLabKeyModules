import React, { useEffect, useRef } from 'react';
import {
    Chart,
    Legend,
    BarController,
    BarElement,
    CategoryScale,
    LinearScale,
    Tooltip
} from 'chart.js';

Chart.register(Legend, BarController, BarElement, CategoryScale, LinearScale, Tooltip);

const colors = [
    'rgb(42, 49, 116)',
    'rgb(75, 77, 135)',
    'rgb(110, 107, 155)',
    'rgb(147, 145, 181)',
    'rgb(194, 192, 210)'
];

export default function BarChart(props) {
    const canvas = useRef(null);

    const { demographics } = props;
    const { fieldName } = props;

    const collectedData = demographics.reduce((acc, curr) => {
        const value = curr[fieldName] === null ? 'Unknown' : curr[fieldName];
        if (acc[value]) {
            acc[value] = acc[value] + 1;
        } else {
            acc[value] = 1;
        }

        return acc;
    }, {});
    const labels = Object.keys(collectedData).sort();
    const data = labels.map(label => collectedData[label]);

    useEffect(() => {
        const chart = new Chart(canvas.current, {
            type: 'bar',
            data: {
                labels,
                datasets: [{
                    label: 'count',
                    data,
                    backgroundColor: colors.slice(0, labels.length)
                }]
            },
            options: {
                responsive: true,
                aspectRatio: 2,
                indexAxis: 'y',
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