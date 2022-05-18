import React from 'react'
import ReactDOM from 'react-dom'
import { ErrorBoundary } from '@labkey/components';

import '../tailwind.css';

import { AnimalRequest } from './animal-request'

// Need to wait for container element to be available in labkey wrapper before render
window.addEventListener('DOMContentLoaded', (event) => {
    ReactDOM.render(
        <ErrorBoundary>
            <AnimalRequest/>
        </ErrorBoundary>, document.getElementById('app'));
});
