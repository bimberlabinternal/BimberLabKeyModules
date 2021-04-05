import React from 'react';
import ReactDOM from 'react-dom';

import { Dashboard } from './Dashboard'

// Need to wait for container element to be available in labkey wrapper before render
window.addEventListener('DOMContentLoaded', (event) => {
    ReactDOM.render(<Dashboard />, document.getElementById('app'));
});