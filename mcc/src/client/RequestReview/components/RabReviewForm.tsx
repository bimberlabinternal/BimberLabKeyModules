import { Filter, getServerContext, Query } from '@labkey/api';
import React, { FormEvent, useState } from 'react';
import { Box, Button, Table, TableBody, TableCell, TableRow, TextField } from '@material-ui/core';

export default function RabReviewForm(props: {requestId: string, readOnly?: boolean}) {
    const [ recordData, setRecordData ] = useState(null)

    const filters = [
        Filter.create('requestId', props.requestId)
    ]

    // NOTE: consider re-naming this param. When readOnly=false, this form is used for the current user to enter their review.
    // When readOnly=true, this is used to summarize the reviews from all reviewers.
    if (!props.readOnly) {
        filters.push(Filter.create('reviewerid', getServerContext().user.userId))
    }

    Query.selectRows({
        schemaName: "mcc",
        queryName: "requestReviews",
        columns: [
            "rowid",
            "reviewerid",
            "reviewerid/displayName",
            "reviewerid/email",
            "review",
            "score",
            "comments",
            "requestid"
        ],
        filterArray: filters,
        success: function (resp) {
            if (!resp.rows.length) {
                //TODO: error?
            }

            if (resp.rows.review) {
                //TODO: this indicates the review was already entered. Do we want to allow updates?
            }

            setRecordData({...resp.rows[0]})
        },
        failure: function(response) {
            alert(response.exception)
        }
    })

    if (!recordData) {
        return(<div>Loading...</div>)
    }

    if (props.readOnly) {
        return(<div>This needs to render a simple read-only table of the reviews</div>)
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

        // TODO: save this record and navigateBack to
    }

    return (
        <>
        <h2>Enter MCC Review</h2>
        <form noValidate autoComplete='off' onSubmit={onFormSubmit}>
        <Box style={{display: 'inline-block'}}>
        <Table width={500}>
            <TableBody>
            <TableRow>
                {/*TODO: this probably needs to be a select*/}
                <TableCell><TextField key={"review"} label={"Review"} onChange={handleChange} variant={'outlined'} value={recordData.review || ''} disabled={true} fullWidth={true}/></TableCell>
            </TableRow>
            <TableRow>
                <TableCell><TextField key={"comments"} label={"Comments"} minRows={4} multiline={true} onChange={handleChange} variant={'outlined'} defaultValue={recordData.comments || ''} fullWidth={true} /></TableCell>
            </TableRow>
            </TableBody>
        </Table>
        <p />
        <Button variant={"contained"} style={{marginRight: 10}} type={'submit'}>Submit Review</Button>
        </Box>
        </form>
        </>
    )
}