import React, { useEffect, useRef, useState } from 'react';
import {
    Chart,
    Legend,
    ScatterController,
    ScatterControllerDatasetOptions,
    ChartConfiguration,
    ScaleChartOptions,
    CategoryScale,
    LinearScale,
    Tooltip, ChartData, DefaultDataPoint
} from 'chart.js';

Chart.register(Legend, ScatterController, CategoryScale, LinearScale, Tooltip);

const colors = [
    'rgb(42, 49, 116)',
    'rgb(75, 77, 135)',
    'rgb(110, 107, 155)',
    'rgb(147, 145, 181)',
    'rgb(194, 192, 210)'
];

export default function ScatterChart(props: {data: any}) {
    console.log('start')
    const canvas = useRef(null);
    const { data } = props;

    useEffect(() => {
        const xField = 'PC1'
        const yField = 'PC2'
        const collectedData = data.map((row) => {
            return {
                x: row[xField],
                y: row[yField]
            }
        });
        console.log(collectedData)

        const chart = new Chart(canvas.current, {
            type: 'scatter',
            data: {
                datasets: [{
                    label: 'Scatter Dataset',
                    data: [{
                        x: -10,
                        y: 0
                    }, {
                        x: 0,
                        y: 10
                    }, {
                        x: 10,
                        y: 5
                    }]
                }]
            },
            options: {
                scales: {

                }
            }
        });
        return () => {
            chart.destroy();
        };
    }, [] /* only run the effect on mount */)

    return (
        <canvas ref={canvas} />
    );
}