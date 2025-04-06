import React from 'react';
import '../tailwind.css';

import { AnimalRequest } from './animal-request';
import { ErrorBoundary } from '../components/ErrorBoundary';
import ReactDOM from 'react-dom';

window.addEventListener('DOMContentLoaded', (event) => {
    ReactDOM.render(
        <ErrorBoundary>
            <AnimalRequest/>
        </ErrorBoundary>, document.getElementById('app')
    );
});