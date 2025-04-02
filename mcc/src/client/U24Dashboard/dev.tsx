import React from 'react';

import ReactDOM from 'react-dom';
import { Dashboard } from './Dashboard';
import { App } from '@labkey/api';

App.registerApp<any>('u24Dashboard', target => {
    ReactDOM.render(<Dashboard />, document.getElementById(target));
}, true);