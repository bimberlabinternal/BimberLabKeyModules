import React from 'react';
import '../tailwind.css';

import { RequestView } from './request-review';
import ReactDOM from 'react-dom';

window.addEventListener('DOMContentLoaded', (event) => {
    ReactDOM.render(<RequestView />, document.getElementById('app'));
});