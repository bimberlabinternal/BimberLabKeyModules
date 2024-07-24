import React from 'react';
import ReactDOM from 'react-dom';
import { App } from '@labkey/api';

import { Dashboard } from '../Dashboard';

const render = (target: string) => {
    ReactDOM.render(<Dashboard />, document.getElementById(target));
};

App.registerApp<any>('u24DashboardWebpart', render, true /* hot */);
