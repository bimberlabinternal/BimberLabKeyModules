import React, { FormEvent, useEffect, useState } from 'react';
import { ActionURL, Filter, getServerContext, Query } from '@labkey/api';
import {
    Box,
    Button,
    makeStyles,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableRow,
    TextField
} from '@material-ui/core';
import { AnimalRequestModel } from '../../components/RequestUtils';
import SavingOverlay from '../../AnimalRequest/saving-overlay';

export default function FinalReviewForm(props: {requestData: AnimalRequestModel}) {
    const { requestData } = props
    const [ recordData, setRecordData ] = useState(null)
    const [ reviewData, setReviewData ] = useState(null)
    const [ displayOverlay, setDisplayOverlay ] = useState(false)
    const [ hasSubmitted, setHasSubmitted ] = useState(false)
    const [ pendingStatus, setPendingStatus ] = useState<string>(null)

    const styles = makeStyles({
        tableHead: {
            fontWeight: "bold",
            padding: 5,
            paddingTop: 0
        },
        tableCell: {
            border: 1,
            borderColor: "black",
            borderStyle: "solid",
            padding: 5
        }
    })()

    useEffect(() => {
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
        setHasSubmitted(true)
        e.preventDefault()
        if (!e.currentTarget.reportValidity()) {
            return
        }

        setDisplayOverlay(true)
        Query.updateRows({
            schemaName: "mcc",
            queryName: "requestScores",
            rows: [recordData],
            success: function (resp) {
                updateRequestStatus(pendingStatus)
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
                    <TableCell className={styles.tableHead}>Reviewer</TableCell>
                    <TableCell className={styles.tableHead}>Review</TableCell>
                    <TableCell className={styles.tableHead}>Comments</TableCell>
                </TableRow>
            </TableHead>
            <TableBody>
                {reviewData.map((row, idx) => {
                    return(
                        <TableRow key={"review-" + row.reviewerid} style = { idx % 2 ? { background : "#fdffe0" }:{ background : "white" }}>
                            <TableCell className={styles.tableCell}>{row['reviewerid/displayName']}</TableCell>
                            <TableCell className={styles.tableCell}>{row.review}</TableCell>
                            <TableCell className={styles.tableCell}>{row.comments}</TableCell>
                        </TableRow>
                    )
                })}
            </TableBody>
        </Table>
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
                    <TableCell><TextField key={"proposalScore"} name={"proposalScore"} required={true} error={hasSubmitted && !recordData.proposalScore} label={"Final Proposal Score"} onChange={handleChange} variant={'outlined'} defaultValue={recordData.proposalScore || ''} fullWidth={true} /></TableCell>
                </TableRow>
                <TableRow>
                    <TableCell><TextField key={"comments"} name={"comments"} label={"Comments"} minRows={4} multiline={true} onChange={handleChange} variant={'outlined'} defaultValue={recordData.comments || ''} fullWidth={true} /></TableCell>
                </TableRow>
                </TableBody>
            </Table>
            <Button key={"approveBtn"} variant={"contained"} style={{marginRight: 10}} type={'submit'} onClick={() => setPendingStatus("Approved")}>Approve Request</Button>
            <Button key={"rejectBtn"} variant={"contained"} style={{marginRight: 10}} type={'submit'}  onClick={() => setPendingStatus("Rejected")}>Reject Request</Button>
            </form>
        </Box>
        <SavingOverlay display={displayOverlay} />
        </>
    )
}