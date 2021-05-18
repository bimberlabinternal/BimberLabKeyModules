import React from 'react';
import ReactDOM from 'react-dom';
import { AppContainer } from 'react-hot-loader';
import { App } from '@labkey/api';

import { Dashboard } from "../Dashboard";

App.registerApp<any>('dashboardWebpart', (target: string) => {
    ReactDOM.render(
        <AppContainer>
            <Dashboard />
        </AppContainer>,
        document.getElementById(target)
    );
}, true /* hot */);

declare const module: any;
