import React from 'react';
import '../tailwind.css';

import { AnimalRequest } from './animal-request';
import { ErrorBoundary } from '../components/ErrorBoundary';
import ReactDOM from 'react-dom';
import { App } from '@labkey/api';

App.registerApp<any>('mccAnimalRequest', (target: string) => {
    ReactDOM.render(
        <ErrorBoundary>
            <AnimalRequest/>
        </ErrorBoundary>, document.getElementById('app')
    );
});