import React, { useEffect, useState } from 'react';
import { ActionURL, Filter, Query } from '@labkey/api';
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Typography } from '@material-ui/core';
import { AnimalRequestModel } from '../../components/RequestUtils';
import SavingOverlay from '../../AnimalRequest/saving-overlay';

export default function InternalReviewForm(props: {requestData: AnimalRequestModel}) {
    const { requestData } = props
    const [ recordData, setRecordData ] = useState(null)
    const [ rabMembers, setRabMembers ] = useState(null)
    const [ existingAssignments, setExistingAssignments ] = useState(null)
    const [ displayOverlay, setDisplayOverlay ] = useState(false)

    const [ showReturnToInvestigator, setShowReturnToInvestigator ] = useState(false)
    const [ showSendToRab, setShowSendToRab ] = useState(false)

    useEffect(() => {
        Query.selectRows({
            schemaName: "mcc",
            queryName: "requestScores",
            columns: [
                "rowid",
                "preliminaryScore",
                "resourceAvailabilityAssessment",
                "proposalScore",
                "comments",
                "requestid",
                "requestid/createdby/email"

            ],
            filterArray: [
                Filter.create('requestId', requestData.request.objectid)
            ],
            success: function (resp) {
                setRecordData(resp.rows[0] ? {...resp.rows[0]} : {})
            },
            failure: function (response) {
                console.error(response)
                alert(response.exception)
            }
        })

        Query.selectRows({
            schemaName: 'core',
            queryName: 'Members',
            columns: 'UserId,UserId/DisplayName',
            filterArray: [
                Filter.create('GroupId/Name', 'MCC RAB Members')
            ],
            success: function (resp) {
                setRabMembers(resp.rows)
            },
            failure: function (response) {
                console.error(response)
                alert(response.exception)
            }
        })

        Query.selectRows({
            schemaName: "mcc",
            queryName: "requestReviews",
            columns: 'reviewerId',
            filterArray: [
                Filter.create('requestId', requestData.request.objectid)
            ],
            success: function (resp) {
                setExistingAssignments(resp.rows?.map(r => r.reviewerId))
            },
            failure: function (response) {
                console.error(response)
                alert(response.exception)
            }
        })
    }, [requestData])

    if (!recordData || !rabMembers || !existingAssignments || !requestData) {
        return(<div>Loading...</div>)
    }

    const onReturnToInvestigatorSubmit = (e) => {
        updateRequestStatus('Draft')
        setShowReturnToInvestigator(false)
    }

    const updateRequestStatus = (status: string) => {
        setDisplayOverlay(true)
        Query.updateRows({
            schemaName: "mcc",
            queryName: "animalRequests",
            rows: [{
                rowid: requestData.request.rowid,
                objectid: requestData.request.objectid,
                status: status
            }],
            success: function (resp) {
                requestData.request.status = status
                setDisplayOverlay(false)
                window.location.href = ActionURL.buildURL("mcc", "mccRequestAdmin")
            },
            failure: function (response) {
                setDisplayOverlay(false)
                console.error(response)
                alert(response.exception)
            }
        })
    }

    const onRabReviewSubmit = (e) => {
        setDisplayOverlay(true)
        const toInsert = rabMembers.filter(r => !existingAssignments.includes(r.UserId)).map((r, idx) => {
            return {
                reviewerId: r.UserId,
                requestId: requestData.request.objectid,
                role: 'Reviewer ' + idx
            }
        })

        if (!toInsert.length) {
            updateRequestStatus('RAB Review')
        }
        else {
            Query.insertRows({
                schemaName: "mcc",
                queryName: "requestReviews",
                rows: toInsert,
                success: function (resp) {
                    updateRequestStatus('RAB Review')
                },
                failure: function (response) {
                    setDisplayOverlay(false)
                    console.error(response)
                    alert(response.exception)
                }
            })
        }

        setShowSendToRab(false)
    }

    const emailHref = recordData['requestid/createdby/email'] ? ("mailto:" + recordData['requestid/createdby/email'] + "?subject=MCC Request: " + recordData.rowid) : null

    return (
        <>
        <h2>MCC Internal Review</h2>
        <Typography style={{paddingBottom: 20}}>After reviewing the request above for completeness and accuracy, please either return to the investigator or assign for RAB Review</Typography>
        <Box key={"mccReviewBox"} style={{display: 'inline-block'}}>
            <Button href={emailHref} variant={"contained"} style={{marginRight: 10}} disabled={!emailHref}>Contact Investigator</Button>
            <Button key={"returnToPiBtn"} variant={"contained"} style={{marginRight: 10}} onClick={(e) => setShowReturnToInvestigator(true)}>Return to Investigator</Button>
            <Button key={"rabAssignBtn"} variant={"contained"} style={{marginRight: 10}} onClick={(e) => setShowSendToRab(true)}>Assign to RAB Reviewers</Button>
        </Box>
        <Dialog open={showReturnToInvestigator}>
            <DialogTitle>Return To Investigator</DialogTitle>
            <DialogContent>
                Hitting submit will change the status of this request back to draft. Please use the button below to contact the investigator with questions or concerns.
            </DialogContent>
            <DialogActions>
                <Box mr="5px">
                    <Button onClick={onReturnToInvestigatorSubmit} disabled={false}>Submit</Button>
                    <Button onClick={(e) => setShowReturnToInvestigator(false)}>Close</Button>
                </Box>
            </DialogActions>
        </Dialog>
        <Dialog open={showSendToRab}>
            <DialogTitle>Assign for RAB Review</DialogTitle>
            <DialogContent>
                This will assign this review to review by the RAB, which includes:
                {
                    rabMembers.map(r => {
                        return(<Typography key={'rab-' + r.UserId} style={{paddingTop: 20}}>{r['UserId/DisplayName'] + (existingAssignments.includes(r.UserId) ? ' (already assigned)' : '')}</Typography>)
                    })
                }
            </DialogContent>
            <DialogActions>
                <Box mr="5px">
                    <Button onClick={onRabReviewSubmit} disabled={false}>Submit</Button>
                    <Button onClick={(e) => setShowSendToRab(false)}>Close</Button>
                </Box>
            </DialogActions>
        </Dialog>
        <SavingOverlay display={displayOverlay} />
        </>
    )
}