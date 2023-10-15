import React from 'react';
import { mount, shallow } from 'enzyme';
import { mocked } from 'jest-mock';

import { Chart } from 'chart.js';
import BarChart from './BarChart';

jest.mock('chart.js');

describe('BarChart', () => {
    const MockChart = mocked(Chart);

    const mockData = [
        {
            gender: 'M'
        },
        {
            gender: 'F'
        },
        {
            gender: 'F'
        }
    ];

    test('it has a canvas element', () => {
        const wrapper = shallow(<BarChart fieldName = "gender" demographics={mockData} />);
        expect(wrapper.find('canvas')).toHaveLength(1);
    });

    test('it renders a chart when mounted', () => {
        mount(<BarChart fieldName = "gender" demographics={mockData} />);
        expect(MockChart).toHaveBeenCalledTimes(1);
    });
});
