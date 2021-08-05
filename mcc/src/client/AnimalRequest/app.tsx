import React from 'react';
import ReactDOM from 'react-dom';

import { AnimalRequest } from './AnimalRequest'

// Need to wait for container element to be available in labkey wrapper before render
window.addEventListener('DOMContentLoaded', (event) => {
    ReactDOM.render(<AnimalRequest />, document.getElementById('app'));
});