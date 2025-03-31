import React from 'react';
import '../tailwind.css';

import { RequestView } from './request-review'
import { createRoot } from 'react-dom/client';

// Need to wait for container element to be available in labkey wrapper before render
window.addEventListener('DOMContentLoaded', (event) => {
    createRoot(document.getElementById('app')).render(<RequestView/>);
});