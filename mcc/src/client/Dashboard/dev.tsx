import React from 'react';
import ReactDOM from 'react-dom';
import { AppContainer } from 'react-hot-loader';

import { Dashboard } from './Dashboard';

const render = () => {
    ReactDOM.render(
        <AppContainer>
            <Dashboard />
        </AppContainer>,
        document.getElementById('app')
    )
};

declare const module: any;

if (module.hot) {
    module.hot.accept();
}

render();
