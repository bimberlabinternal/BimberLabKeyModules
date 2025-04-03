import React from 'react';

import { Dashboard } from './Dashboard';
import ReactDOM from 'react-dom';

window.addEventListener('DOMContentLoaded', (event) => {
    ReactDOM.render(<Dashboard/>, document.getElementById('app'));
}, true);