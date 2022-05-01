import { Filter, getServerContext, Query } from '@labkey/api';
import React, { FormEvent, useState } from 'react';
import {
    Box,
    Button,
    MenuItem,
    Select,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableRow,
    TextField
} from '@material-ui/core';

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
            setRecordData({...resp.rows})
        },
        failure: function(response) {
            alert(response.exception)
        }
    })

    if (!recordData) {
        return(<div>Loading...</div>)
    }

    if (props.readOnly) {
        return(
            <Table>
                <TableHead>
                    <TableRow key={"header"}><TableCell>Reviewer</TableCell><TableCell>Score</TableCell><TableCell>Comments</TableCell></TableRow>
                </TableHead>
                <TableBody>
                    {recordData.map(row => {
                        return(<TableRow key={"review-" + row.reviewerid}><TableCell>{row.reviewerid}</TableCell><TableCell>{row.score}</TableCell><TableCell>{row.comments}</TableCell></TableRow>)
                    })}
                </TableBody>
            </Table>
        )
    }

    if (!recordData?.length) {
        return(<div style={{paddingTop: 20}}>You have not been assigned to review this request</div>)
    }

    const handleChange = (e) => {
        recordData[0][e.target.name] = e.target.value ? e.target.value.trim() : null
        setRecordData([...recordData])

        console.log(e.target.name)
        console.log(e.target.value.trim())
        console.log(recordData)
    };

    const onFormSubmit = (e: FormEvent) => {
        e.preventDefault()

        console.log(recordData)

        // TODO: save this record and navigateBack to the prior page
    }

    return (
        <>
        <h2>Enter Review</h2>
        <form noValidate autoComplete='off' onSubmit={onFormSubmit}>
        <Box style={{display: 'inline-block'}}>
            After reviewing the request, please fill out the section below and provide a 2-3 sentence justification for your choice.
            <Table width={500}>
            <TableBody>
            <TableRow>
                <TableCell>
                    <Select id={"review"} aria-label="Review" onChange={handleChange} required={true} defaultValue={recordData[0].review} fullWidth={true} displayEmpty={true}>
                        <MenuItem value={"I recommend this proposal"}>I recommend this proposal</MenuItem>
                        <MenuItem value={"I recommend this proposal with conditions"}>I recommend this proposal with conditions</MenuItem>
                        <MenuItem value={"I do not recommend this proposal"}>I do not recommend this proposal</MenuItem>
                    </Select>
                </TableCell>
            </TableRow>
            <TableRow>
                <TableCell><TextField key={"comments"} label={"Justification"} minRows={4} multiline={true} onChange={handleChange} variant={'outlined'} defaultValue={recordData[0].comments || ''} fullWidth={true} /></TableCell>
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