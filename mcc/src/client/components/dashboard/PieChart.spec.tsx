import React from 'react';
import { mount, shallow } from 'enzyme';
import { mocked } from 'jest-mock';
import { describe, expect, jest, test } from '@jest/globals';

import { Chart } from 'chart.js';
import PieChart from './PieChart';

jest.mock('chart.js');

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

describe('PieChart', () => {
    const MockChart = mocked(Chart);

    test('it has a canvas element', () => {
        const wrapper = shallow(<PieChart fieldName = "gender" demographics={mockData} />);
        expect(wrapper.find('canvas')).toHaveLength(1);
    });

    test('it renders a chart when mounted', () => {
        mount(<PieChart fieldName = "gender" demographics={mockData} />);
        expect(MockChart).toHaveBeenCalledTimes(1);
    });
});
