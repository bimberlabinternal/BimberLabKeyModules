import React from 'react';

import ReactDOM from 'react-dom';
import { Dashboard } from './Dashboard';

window.addEventListener('DOMContentLoaded', (event) => {
    ReactDOM.render(<Dashboard />, document.getElementById('app'));
}, true);