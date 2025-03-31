import React from 'react';
import { createRoot } from 'react-dom/client';
import '../tailwind.css';

import { AnimalRequest } from './animal-request';
import { ErrorBoundary } from '../components/ErrorBoundary';

// Need to wait for container element to be available in labkey wrapper before render
window.addEventListener('DOMContentLoaded', (event) => {
    createRoot(document.getElementById('app')).render((
        <ErrorBoundary>
            <AnimalRequest/>
        </ErrorBoundary>)
    );
});