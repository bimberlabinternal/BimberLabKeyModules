import {
    Chart as ChartJS,
    LinearScale,
    PointElement,
    LineElement,
    Tooltip,
    Legend
} from 'chart.js'
import palette from 'google-palette'

import { Scatter } from 'react-chartjs-2';
import React, { useEffect, useRef, useState } from 'react';

ChartJS.register(LinearScale, PointElement, LineElement, Tooltip, Legend);

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
    const uniqueColonies = [...new Set(collectedData.map(x => String(x.colony)))]
    const colors = palette(['Set1', 'qualitative'], uniqueColonies.length);

    uniqueColonies.forEach((colonyName : string, idx) => {
        dataByColony.push({
            label: colonyName,
            backgroundColor: '#' + colors[idx],
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