import React from 'react';
import { mount, shallow } from 'enzyme';
import { mocked } from 'ts-jest/utils';
import { jest, describe, expect, test, beforeEach } from '@jest/globals';

import { Chart } from 'chart.js';
import PieChart from './PieChart';

jest.mock('chart.js');
const MockChart = mocked(Chart, true);

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
    test('it has a canvas element', () => {
        const wrapper = shallow(<PieChart fieldName = "gender" demographics={mockData} />);
        expect(wrapper.find('canvas')).toHaveLength(1);
    });

    test('it renders a chart when mounted', () => {
        mount(<PieChart fieldName = "gender" demographics={mockData} />);
        expect(MockChart).toHaveBeenCalledTimes(1);
    });
});
