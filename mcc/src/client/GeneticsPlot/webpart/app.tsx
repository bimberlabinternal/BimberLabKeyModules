import React from 'react';
import ReactDOM from 'react-dom';
import { App } from '@labkey/api';

import { GeneticsPlot } from '../GeneticsPlot';

App.registerApp<any>('mccPcaWebpart', target => {
    ReactDOM.render(<GeneticsPlot />, document.getElementById(target));
});
