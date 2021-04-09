import React from 'react';
import { mount, shallow } from 'enzyme';
import { mocked } from 'ts-jest/utils';
import { jest, describe, expect, test, beforeEach } from '@jest/globals';

import { Chart } from 'chart.js';
import GenderChart from './GenderChart';

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

describe('GenderChart', () => {
    test('it has a canvas element', () => {
        const wrapper = shallow(<GenderChart demographics={mockData} />);
        expect(wrapper.find('canvas')).toHaveLength(1);
    });

    test('it renders a chart when mounted', () => {
        mount(<GenderChart demographics={mockData} />);
        expect(MockChart).toHaveBeenCalledTimes(1);
    });
});
