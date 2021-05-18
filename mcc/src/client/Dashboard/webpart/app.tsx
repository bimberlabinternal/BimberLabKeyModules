import React from 'react';
import ReactDOM from 'react-dom';
import { App } from '@labkey/api';

import { Dashboard } from '../Dashboard';

App.registerApp<any>('dashboardWebpart', target => {
    ReactDOM.render(<Dashboard />, document.getElementById(target));
});
