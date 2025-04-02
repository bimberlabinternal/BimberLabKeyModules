import React from 'react';

import { GeneticsPlot } from './GeneticsPlot'
import ReactDOM from 'react-dom';
import { App } from '@labkey/api';

App.registerApp<any>('geneticsPlot', target => {
    ReactDOM.render(<GeneticsPlot />, document.getElementById(target));
});