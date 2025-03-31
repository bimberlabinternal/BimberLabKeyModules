import React from 'react';
import { Dashboard } from './Dashboard'
import { createRoot } from 'react-dom/client';

// Need to wait for container element to be available in labkey wrapper before render
window.addEventListener('DOMContentLoaded', (event) => {
    createRoot(document.getElementById('app')).render(<Dashboard/>);
});