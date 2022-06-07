import {
    Chart as ChartJS,
    LinearScale,
    PointElement,
    LineElement,
    Tooltip,
    Legend
} from 'chart.js';

import { Scatter } from 'react-chartjs-2';
import React, { useEffect, useRef, useState } from 'react';

ChartJS.register(LinearScale, PointElement, LineElement, Tooltip, Legend);

const colors = [
    'rgb(42, 49, 116)',
    'rgb(75, 77, 135)',
    'rgb(110, 107, 155)',
    'rgb(147, 145, 181)',
    'rgb(194, 192, 210)'
];

export default function ScatterChart(props: {data: any}) {
    const { data } = props;

    const idField = 'MarmID'
    const xField = 'PC1'
    const yField = 'PC2'
    const collectedData = data.map((row) => {
        return {
            animalId: row[idField],
            x: row[xField],
            y: row[yField]
        }
    });

    const chartOptions = {
        scales: {
            y: {
                title: {
                    display: true,
                    text: yField
                }
            },
            x: {
                title: {
                    display: true,
                    text: xField
                }
            }
        },
        responsive: true,
        plugins: {
            legend: {
                display: false
            },
            tooltip: {
                callbacks: {
                    label: function (context) {
                        let label = ['ID: ' + context.raw.animalId]
                        label.push(xField + ': ' + context.parsed.x)
                        label.push(yField + ': ' + context.parsed.y)

                        return label;
                    }
                }
            }
        }
    };

    const chartData = {
        datasets: [{
            data: collectedData,
            backgroundColor: 'rgba(255, 99, 132, 1)'
        }]
    }

    return (
        <Scatter data={chartData} options={chartOptions}/>
    );
}