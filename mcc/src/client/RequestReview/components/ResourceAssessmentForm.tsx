import React, { useEffect, useState } from 'react';
import { ActionURL, Filter, Query } from '@labkey/api';
import { Box, Button, Table, TableBody, TableCell, TableHead, TableRow, TextField } from '@mui/material';
import { AnimalRequestModel } from '../../components/RequestUtils';
import SavingOverlay from '../../AnimalRequest/saving-overlay';
import { styled } from '@mui/styles';

const StyledTableHead = styled(TableCell)(({ theme }) => ({
    fontWeight: "bold",
    padding: 5,
    paddingTop: 0
}))

const StyledTableCell = styled(TableCell)(({ theme }) => ({
    border: 1,
    borderColor: "black",
    borderStyle: "solid",
    padding: 5
}))

export default function ResourceAssessmentForm(props: {requestData: AnimalRequestModel}) {
    const { requestData } = props
    const [ recordData, setRecordData ] = useState(null)
    const [ reviewData, setReviewData ] = useState(null)
    const [ displayOverlay, setDisplayOverlay ] = useState(false)

    useEffect(() => {
        Query.selectRows({
            schemaName: "mcc",
            queryName: "requestScores",
            columns: [
                "rowid",
                "preliminaryScore",
                "resourceAvailabilityAssessment",
                "requestid"
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
            filterArray: [
                Filter.create('requestId', requestData.request.objectid)
            ],
            success: function (resp) {
                setReviewData([...resp.rows])
            },
            failure: function (response) {
                alert(response.exception)
            }
        })
    }, [requestData])

    if (!recordData || !reviewData) {
        return(<div>Loading...</div>)
    }

    const handleChange = (e) => {
        setRecordData({...recordData, [e.target.name]: e.target.value ? e.target.value.trim() : null})
    };

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

    const onFormSubmit = (e: React.SyntheticEvent<HTMLFormElement>) => {
        e.preventDefault()
        if (!e.currentTarget.reportValidity()) {
            return
        }

        setDisplayOverlay(true)
        Query.updateRows({
            schemaName: "mcc",
            queryName: "requestScores",
            rows: [{
                rowid: recordData.rowid,
                resourceAvailabilityAssessment: recordData.resourceAvailabilityAssessment
            }],
            success: function (resp) {
                updateRequestStatus("Decision Pending")
            },
            failure: function (response) {
                setDisplayOverlay(false)
                console.error(response)
                alert(response.exception)
            }
        })
    }

    return(
        <>
        <h2>RAB Reviews</h2>
        <Table style={{display: "inline-block", padding: 5}}>
            <TableHead>
                <TableRow key={"header"}>
                    <StyledTableHead>Reviewer</StyledTableHead>
                    <StyledTableHead>Review</StyledTableHead>
                    <StyledTableHead>Comments</StyledTableHead>
                </TableRow>
            </TableHead>
            <TableBody>
                {reviewData.map((row, idx) => {
                    return(
                        <TableRow key={"review-" + row.reviewerid} style = { idx % 2 ? { background : "#fdffe0" }:{ background : "white" }}>
                            <StyledTableCell>{row['reviewerid/displayName']}</StyledTableCell>
                            <StyledTableCell>{row.review}</StyledTableCell>
                            <StyledTableCell>{row.comments}</StyledTableCell>
                        </TableRow>
                    )
                })}
            </TableBody>
        </Table>
        <h2>Enter Resource Availability Assessment</h2>
        <form key={"resourceAssessmentForm"} noValidate autoComplete='off' onSubmit={onFormSubmit}>
        <Box key={"mccReviewBox"} style={{display: 'inline-block'}}>
            <Table>
                <TableBody>
                <TableRow>
                    <TableCell><TextField key={"preliminaryScore"} name={"preliminaryScore"} label={"Preliminary Score"} onChange={handleChange} variant={'outlined'} value={recordData.preliminaryScore || ''} disabled={true} fullWidth={true}/></TableCell>
                </TableRow>
                <TableRow>
                    <TableCell width={800}><TextField key={"resourceAvailabilityAssessment"} name={"resourceAvailabilityAssessment"} label={"Resource Availability Assessment"} required={true} minRows={4} multiline={true} onChange={handleChange} variant={'outlined'} defaultValue={recordData.resourceAvailabilityAssessment || ''} fullWidth={true} /></TableCell>
                </TableRow>
                </TableBody>
            </Table>
            <Button key={"approveBtn"} variant={"contained"} style={{marginRight: 10}} type={'submit'}>Submit</Button>
        </Box>
        </form>
        <SavingOverlay display={displayOverlay} />
        </>
    )
}