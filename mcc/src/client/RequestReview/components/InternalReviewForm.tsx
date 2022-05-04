import React, { FormEvent, useState } from 'react';
import { Filter, Query } from '@labkey/api';
import { Box, Button, Dialog, DialogTitle, Table, TableBody, TableCell, TableRow, TextField } from '@material-ui/core';

export default function InternalReviewForm(props: {requestId: string, readOnly?: boolean}) {
    const [ recordData, setRecordData ] = useState(null)
    const [ showReturnToInvestigator, setShowReturnToInvestigator ] = useState(false)
    const [ showSendToRab, setShowSendToRab ] = useState(false)

    Query.selectRows({
        schemaName: "mcc",
        queryName: "requestScores",
        columns: [
            "rowid",
            "preliminaryScore",
            "resourceAvailabilityScore",
            "proposalScore",
            "comments",
            "requestid"
        ],
        filterArray: [
            Filter.create('requestId', props.requestId)
        ],
        success: function (resp) {
            setRecordData(resp.rows[0] ? {...resp.rows[0]} : {})
        },
        failure: function(response) {
            console.error(response)
            alert(response.exception)
        }
    })

    if (!recordData) {
        return(<div>Loading...</div>)
    }

    if (props.readOnly) {
        return(<div>This needs to render a simple read-only table of the fields so RAB reviewers have this information</div>)
    }

    const handleChange = (e) => {
        setRecordData({...recordData, [e.target.value]: e.target.value ? e.target.value.trim() : null})

        console.log(recordData[e.target.name])
    };

    const onFormSubmit = (e: FormEvent) => {
        e.preventDefault()

        console.log(recordData)

        // TODO: save this record and also update the original request record with new status
    }

    return (
        <>
        <h2>Enter MCC Review</h2>
        <Box key={"mccReviewBox"} style={{display: 'inline-block'}}>
            <form key={"internalReviewForm"} noValidate autoComplete='off' onSubmit={onFormSubmit}>
            <Table width={500}>
                <TableBody>
                <TableRow>
                    <TableCell><TextField key={"preliminaryScore"} name={"preliminaryScore"} label={"Preliminary Score"} onChange={handleChange} variant={'outlined'} value={recordData.preliminaryScore || ''} disabled={true} fullWidth={true}/></TableCell>
                </TableRow>
                <TableRow>
                    <TableCell><TextField key={"resourceAvailabilityScore"} name={"resourceAvailabilityScore"} label={"Resource Availability Score"} onChange={handleChange} variant={'outlined'} defaultValue={recordData.resourceAvailabilityScore || ''} fullWidth={true} /></TableCell>
                </TableRow>
                <TableRow>
                    <TableCell><TextField key={"proposalScore"} name={"proposalScore"} label={"Final Proposal Score"} onChange={handleChange} variant={'outlined'} defaultValue={recordData.proposalScore || ''} fullWidth={true} /></TableCell>
                </TableRow>
                <TableRow>
                    <TableCell><TextField key={"comments"} name={"comments"} label={"Comments"} minRows={4} multiline={true} onChange={handleChange} variant={'outlined'} defaultValue={recordData.comments || ''} fullWidth={true} /></TableCell>
                </TableRow>
                </TableBody>
            </Table>
            <Button key={"saveReviewBtn"} variant={"contained"} style={{marginRight: 10}} type={'submit'}>Save Review</Button>
            <Button key={"returnToPiBtn"} variant={"contained"} style={{marginRight: 10}} type={'button'} onClick={(e) => setShowReturnToInvestigator(true)}>Return to Investigator</Button>
            <Button key={"rabAssignBtn"} variant={"contained"} style={{marginRight: 10}} type={'button'} onClick={(e) => setShowSendToRab(true)}>Assign to RAB Reviewers</Button>
            <Dialog open={showReturnToInvestigator}>
                <DialogTitle>Return To Investigator</DialogTitle>

            </Dialog>
            <Dialog open={showSendToRab}>
                <DialogTitle>Assign for RAB Review</DialogTitle>
            </Dialog>
            </form>
        </Box>
        </>
    )
}