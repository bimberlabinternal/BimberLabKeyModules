import React from 'react';
import ReactDOM from 'react-dom';
import { App } from '@labkey/api';

import { GeneticsPlot } from '../GeneticsPlot';

const render = (target: string) => {
    ReactDOM.render(<GeneticsPlot />, document.getElementById(target));
};

App.registerApp<any>('mccPcaWebpart', render, true /* hot */);