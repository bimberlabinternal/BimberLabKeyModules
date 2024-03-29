import React from 'react';
import ReactDOM from 'react-dom';
import { AppContainer } from 'react-hot-loader';

import { GeneticsPlot } from './GeneticsPlot';

const render = () => {
    ReactDOM.render(
        <AppContainer>
            <GeneticsPlot />
        </AppContainer>,
        document.getElementById('app')
    )
};

declare const module: any;

if (module.hot) {
    module.hot.accept();
}

render();
