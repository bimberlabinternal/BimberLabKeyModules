import React from 'react';

import { GeneticsPlot } from './GeneticsPlot';
import ReactDOM from 'react-dom';

window.addEventListener('DOMContentLoaded', (event) => {
    ReactDOM.render(<GeneticsPlot />, document.getElementById('app'));
});