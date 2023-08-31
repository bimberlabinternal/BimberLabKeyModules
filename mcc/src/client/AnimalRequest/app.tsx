import React from 'react';
import ReactDOM from 'react-dom';

import '../tailwind.css';

import { AnimalRequest } from './animal-request';
import { ErrorBoundary } from '../components/ErrorBoundary';

// Need to wait for container element to be available in labkey wrapper before render
window.addEventListener('DOMContentLoaded', (event) => {
    ReactDOM.render(
        <ErrorBoundary>
            <AnimalRequest/>
        </ErrorBoundary>, document.getElementById('app'));
});
