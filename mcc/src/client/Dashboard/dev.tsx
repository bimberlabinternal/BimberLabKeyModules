import React from 'react';
import ReactDOM from 'react-dom';

import { Dashboard } from './Dashboard';

const render = () => {
    ReactDOM.render(<Dashboard />, document.getElementById('app'));
};

render();
