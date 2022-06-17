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

const CHART_COLORS = [
    'rgb(255, 99, 132)', //red
    'rgb(255, 159, 64)', //orange
    'rgb(255, 205, 86)', //yellow
    'rgb(75, 192, 192)', //green
    'rgb(54, 162, 235)', //blue
    'rgb(153, 102, 255)', //purple
    'rgb(201, 203, 207)' //grey
]

export default function ScatterChart(props: {data: any}) {
    const { data } = props;

    const idField = 'MarmID'
    const xField = 'PC1'
    const yField = 'PC2'
    const collectedData = data.map((row) => {
        return {
            animalId: row[idField],
            x: row[xField],
            y: row[yField],
            colony: row.Colony || 'Unknown',
            sex: row.Sex || 'Unknown',
        }
    });

    const dataByColony = []
    const uniqueColonies = [...new Set(collectedData.map(x => x.colony))]
    uniqueColonies.forEach((colonyName : string, idx) => {
        dataByColony.push({
            label: colonyName,
            backgroundColor: CHART_COLORS[idx],
            data: collectedData.filter(x => x.colony == colonyName)
        })
    })

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
                display: true
            },
            tooltip: {
                callbacks: {
                    label: function (context) {
                        let label = ['ID: ' + context.raw.animalId]
                        label.push('Sex: ' + context.raw.sex)
                        label.push('Colony: ' + context.raw.colony)
                        label.push(xField + ': ' + context.parsed.x)
                        label.push(yField + ': ' + context.parsed.y)

                        return label;
                    }
                }
            }
        }
    };

    const chartData = {
        datasets: dataByColony
    }

    return (
        <Scatter data={chartData} options={chartOptions}/>
    );
}