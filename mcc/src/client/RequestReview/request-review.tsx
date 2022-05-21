import React, { useState } from 'react';
import { AnimalRequestModel, queryRequestInformation } from '../components/RequestUtils';
import { default as ReadOnlyRequest } from './components/ReadOnlyRequest';
import Title from '../AnimalRequest/components/title';
import RabReviewForm from './components/RabReviewForm';
import InternalReviewForm from './components/InternalReviewForm';

import { ThemeProvider } from '@material-ui/styles';
import { createTheme } from '@material-ui/core/styles';
import '../labkeyOverrides.css';
import FinalReviewForm from './components/FinalReviewForm';
import { ErrorBoundary } from '@labkey/components';
import ResourceAssessmentForm from './components/ResourceAssessmentForm';

export function RequestView() {
    const mode = (new URLSearchParams(window.location.search)).get("mode")
    const requestId = (new URLSearchParams(window.location.search)).get("requestId")

    const [requestData, setRequestData] = useState<AnimalRequestModel>(null)

    if (!requestId) {
        return(<div>No request Id provided</div>)
    }

    if (!mode) {
        return(<div>Must provide the mode parameter for this page on the URL</div>)
    }

    if (!requestData) {
        queryRequestInformation(requestId, handleFailure).then((model) => {
            setRequestData(model)
        })
    }

    if (!requestData || !requestData?.dataLoaded) {
        return (
            <div className="tw-flex tw-justify-center tw-items-center">
                <div style={{ borderBottom: "2px solid #3495d2" }} className="tw-animate-spin tw-rounded-full tw-h-32 tw-w-32"></div>
            </div>
        )
    }
    else if (requestData.dataLoaded === true && !requestData.request) {
        return(<Title text="No such request."/>)
    }

    function handleFailure(response) {
        alert(response.exception)  //this is probably what you want to show. An example would be to submit data with a long value for middle initial (>14 characters)

        setRequestData(requestData)
    }

    let reviewForms = null
    switch (mode) {
        case "details":
            break
        case "primaryReview":
            reviewForms = [
                <InternalReviewForm key={"internalReviewForm"} requestData={requestData}/>
            ]
            break
        case "rabReview":
            reviewForms = (
                <>
                <RabReviewForm key={"rabReviewForm"} requestId={requestId}/>
                </>
            )
            break
        case "resourceAvailability":
            reviewForms = (
                <>
                    <ResourceAssessmentForm key={"resourceAssessmentForm"} requestData={requestData}/>
                </>
            )
            break
        case "finalReview":
            reviewForms = (
                <>
                <FinalReviewForm key={"finalReviewForm"} requestData={requestData}/>
                </>
            )
            break
    }

    const theme = createTheme({
        overrides: {
            MuiTableCell: {
                root: {
                    borderBottom: "none"
                }
            }
        }
    });

    return(
        <>
        <ErrorBoundary>
            <ThemeProvider theme={theme}>
                <ReadOnlyRequest requestData={requestData} />
                {reviewForms}
            </ThemeProvider>
        </ErrorBoundary>
        </>
    )
}