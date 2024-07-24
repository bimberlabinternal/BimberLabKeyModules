import React from 'react';
import ReactDOM from 'react-dom';

import { GeneticsPlot } from './GeneticsPlot';

const render = () => {
    ReactDOM.render(<GeneticsPlot />, document.getElementById('app'));
};

render();
