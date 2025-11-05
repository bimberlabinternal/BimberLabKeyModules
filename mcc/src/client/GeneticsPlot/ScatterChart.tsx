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
        <>
        <div style={{paddingBottom: 20, maxWidth: 1000}}>
            The MCC genomic database is extensive, with each individual being genotype at millions of variants
            across the genome. One way to summarize a large dataset can be done using Principal Component Analysis
            (PCA). PCA is a technique used across disciplines (from astronomy to genomics) that reduces the
            information in a multi-dimensional dataset to (fewer) principal components (PC) that retain overall
            trends and patterns in the original data. Biologically, this could mean merging together two variants
            that are always inherited together into just one PC, making the data easier to analyze while maintaining
            its most important patterns.
        </div>
        <Scatter data={chartData} options={chartOptions}/>
        </>
    );
}