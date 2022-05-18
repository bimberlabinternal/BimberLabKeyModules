import React from 'react'
import ReactDOM from 'react-dom'

import '../tailwind.css';

import { RequestView } from './request-review'

// Need to wait for container element to be available in labkey wrapper before render
window.addEventListener('DOMContentLoaded', (event) => {
    ReactDOM.render(<RequestView />, document.getElementById('app'));
});
