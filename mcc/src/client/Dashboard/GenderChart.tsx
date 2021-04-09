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
    'rgb(42, 49, 116)',
    'rgb(75, 77, 135)',
    'rgb(110, 107, 155)',
    'rgb(147, 145, 181)',
    'rgb(194, 192, 210)'
];

export default function GenderChart(props) {
    const canvas = useRef(null);

    const { demographics } = props;
    const mapGender = (gender) => {
        switch (gender) {
            case 'M':
                return 'Male';
            case 'F':
                return 'Female';
            default:
                return 'Unknown';
        }
    };

    const collectedData = demographics.reduce((acc, curr) => {
        const gender = mapGender(curr.gender);
        if (acc[gender]) {
            acc[gender] = acc[gender] + 1;
        } else {
            acc[gender] = 1;
        }

        return acc;
    }, {});
    const labels = Object.keys(collectedData);
    const data = labels.map(label => collectedData[label]);

    useEffect(() => {
        const chart = new Chart<'pie', number[], string>(canvas.current, {
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