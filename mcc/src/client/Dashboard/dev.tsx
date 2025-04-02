import React from 'react';

import { Dashboard } from './Dashboard';
import ReactDOM from 'react-dom';
import { App } from '@labkey/api';

App.registerApp<any>('mccDashboard', (target: string) => {
    ReactDOM.render(<Dashboard/>, document.getElementById('app'));
}, true);