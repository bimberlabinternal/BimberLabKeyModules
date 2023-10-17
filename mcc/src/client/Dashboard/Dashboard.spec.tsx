import React from 'react';
import { mount } from 'enzyme';
import { mocked } from 'jest-mock';
import { beforeEach, describe, expect, jest, test } from '@jest/globals';

import { Dashboard } from './Dashboard';
import { Query } from '@labkey/api';

jest.mock('chart.js');
jest.mock('@labkey/api', () => {
    return {
        Query: {
            selectRows: jest.fn()
        },

        getServerContext: function() {
            return {
                getModuleContext: function() {
                    return {
                        MCCContainer: '/home'
                    }
                }
            };
        }
    }
});

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

describe('Dashboard', () => {
    const mockedQuery = mocked(Query);

    beforeEach(() => {
        mockedQuery.selectRows.mockReset();
        mockedQuery.selectRows.mockImplementation(({ success }) => {
            success({ rows: mockData }, null, null);
            return {} as XMLHttpRequest;
        });
    });

    test('initially renders a loading message', () => {
        mockedQuery.selectRows.mockImplementation(options => {
            return {} as XMLHttpRequest;
        });
        const wrapper = mount(<Dashboard />);
        expect(wrapper.find('.loading')).toHaveLength(1);
    });

    test('queries for data on mount', () => {
        mount(<Dashboard />);
        expect(mockedQuery.selectRows).toHaveBeenCalled();
    });

    test('it renders a grid of cards', () => {
        const wrapper = mount(<Dashboard />);
        expect(wrapper.find('.row')).toHaveLength(4);
        expect(wrapper.find('.panel')).toHaveLength(4);
    });

    test('it displays the result count', () => {
        const wrapper = mount(<Dashboard />);
        const countWrapper = wrapper.find('.count-panel-text');
        expect(countWrapper).toHaveLength(1);
        expect(countWrapper.text()).toEqual(`${mockData.length}`);
    });
});
