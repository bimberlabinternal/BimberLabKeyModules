import React, { FormEvent, useState } from 'react';
import { Filter, Query } from '@labkey/api';
import { Box, Button, Table, TableBody, TableCell, TableRow, TextField } from '@material-ui/core';

export default function InternalReviewForm(props: {requestId: string, readOnly?: boolean}) {
    const [ recordData, setRecordData ] = useState(null)

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
        recordData[e.target.name] = e.target.value ? e.target.value.trim() : null
        setRecordData({...recordData})

        console.log(e.target.name)
        console.log(e.target.value.trim())
        console.log(recordData)
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
                    <TableCell><TextField key={"preliminaryScore"} label={"Preliminary Score"} onChange={handleChange} variant={'outlined'} value={recordData.preliminaryScore || ''} disabled={true} fullWidth={true}/></TableCell>
                </TableRow>
                <TableRow>
                    <TableCell><TextField key={"resourceAvailabilityScore"} label={"Resource Availability Score"} onChange={handleChange} variant={'outlined'} defaultValue={recordData.resourceAvailabilityScore || ''} fullWidth={true} /></TableCell>
                </TableRow>
                <TableRow>
                    <TableCell><TextField key={"proposalScore"} label={"Final Proposal Score"} onChange={handleChange} variant={'outlined'} defaultValue={recordData.proposalScore || ''} fullWidth={true} /></TableCell>
                </TableRow>
                <TableRow>
                    <TableCell><TextField key={"comments"} label={"Comments"} minRows={4} multiline={true} onChange={handleChange} variant={'outlined'} defaultValue={recordData.comments || ''} fullWidth={true} /></TableCell>
                </TableRow>
                </TableBody>
            </Table>
            <Button key={"saveReviewBtn"} variant={"contained"} style={{marginRight: 10}} type={'submit'}>Save Review</Button>
            </form>
        </Box>
        </>
    )
}