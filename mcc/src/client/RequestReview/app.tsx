import React from 'react';
import '../tailwind.css';

import { RequestView } from './request-review';
import ReactDOM from 'react-dom';
import { App } from '@labkey/api';

App.registerApp<any>('mccRequestReview', target => {
    ReactDOM.render(<RequestView />, document.getElementById(target));
});