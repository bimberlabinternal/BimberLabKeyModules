import React, { useState, FormEvent } from 'react'
import { Query, ActionURL, Filter, getServerContext } from '@labkey/api';

export function RequestView() {
    const requestId = (new URLSearchParams(window.location.search)).get("requestId")

    return(<div></div>)

}