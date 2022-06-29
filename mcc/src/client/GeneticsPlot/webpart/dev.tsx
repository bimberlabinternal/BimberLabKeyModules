import React from 'react';
import ReactDOM from 'react-dom';
import { AppContainer } from 'react-hot-loader';
import { App } from '@labkey/api';

import { GeneticsPlot } from '../GeneticsPlot';

App.registerApp<any>('mccPcaWebpart', (target: string) => {
    ReactDOM.render(
        <AppContainer>
            <GeneticsPlot />
        </AppContainer>,
        document.getElementById(target)
    );
}, true /* hot */);

declare const module: any;
