import React from 'react';
import { AnimalRequestModel, AnimalRequestProps } from '../../components/RequestUtils';
import {
    existingMarmosetColonyOptions,
    existingNHPFacilityOptions, fundingSourceOptions,
    IACUCApprovalOptions,
    institutionTypeOptions
} from '../../AnimalRequest/components/values';
import { Box, Button, Grid, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material';
import { ActionURL } from '@labkey/api';
import { styled } from '@mui/styles';

function formatPIName(request: AnimalRequestProps) {
    return(formatName(request.lastname, request.firstname, request.middleinitial))
}

function formatName(lastname, firstname, middleinitial) {
    if (!lastname) {
        return('NOT ENTERED')
    }

    return(lastname + ', ' + (firstname || 'NOT_ENTERED') + (middleinitial ? ' ' + middleinitial : ''))
}

function findDisplayValue(value, translationMap) {
    if (!value) {
        return ''
    }

    return translationMap.filter((rec) => rec.value == value).map((rec) => rec.label).join(', ')
}

function translateRawToDisplayValue(value, translationMap: any[], isMulti = false) {
    if (isMulti) {
        return value.split(',').map((value) => findDisplayValue(value, translationMap))
    }
    else {
        return findDisplayValue(value, translationMap)
    }
}

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

const StyledGridFieldLabel = styled(Grid)(({ theme }) => ({
    fontSize: "14px",
    fontWeight: "bold"
}))

export default function ReadOnlyRequest(props: {requestData: AnimalRequestModel}) {
    const { requestData } = props

    if (!requestData?.dataLoaded) {
        return null
    }

    return(
        <>
        <h2>Request Details</h2>
        <Box display={"inline-block"} style={{marginBottom: 30}}>
            <h4 style={{marginTop: 10}}>Overview</h4>
            <Grid container spacing={1} style={{marginLeft: 10}}>
                <StyledGridFieldLabel item xs={2}>
                    Project Title:
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {requestData.request.title}
                </Grid>
                <StyledGridFieldLabel item xs={2}>
                    Project Narrative:
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {requestData.request.narrative}
                </Grid>
                <StyledGridFieldLabel item xs={2}>
                    Research/disease focus:
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {requestData.request.diseasefocus}
                </Grid>
                <StyledGridFieldLabel item xs={2}>
                    Relation to neuroscience:
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {requestData.request.neuroscience}
                </Grid>
            </Grid>
            <h4 style={{marginTop: 10}}>General Information</h4>
            <Grid container spacing={1} style={{marginLeft: 10}}>
                <StyledGridFieldLabel item xs={2}>
                    PI Name:
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {formatPIName(requestData.request)}
                </Grid>
                <StyledGridFieldLabel item xs={2}>
                    Early State Investigator:
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {requestData.request.earlystageinvestigator ? 'Yes' : 'No'}
                </Grid>
                <StyledGridFieldLabel item xs={2}>
                    <div>Institution: </div>
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {requestData.request.institutionname || ''}
                </Grid>
                <StyledGridFieldLabel item xs={2}>
                    <div>Institution City: </div>
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {requestData.request.institutioncity}, {requestData.request.institutionstate} {requestData.request.institutioncountry}
                </Grid>
                <StyledGridFieldLabel item xs={2}>
                    <div>Institution Type: </div>
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {translateRawToDisplayValue(requestData.request.institutiontype, institutionTypeOptions)}
                </Grid>
                <StyledGridFieldLabel item xs={2}>
                    <div>Signing Official: </div>
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {formatName(requestData.request.officiallastname, requestData.request.officialfirstname, null)}
                    {requestData.request.officialemail ? ' (' + requestData.request.officialemail + ')' : ''}
                </Grid>
                <StyledGridFieldLabel item xs={2}>
                    <div>Co-Investigators: </div>
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {requestData.coinvestigators.length ? requestData.coinvestigators.map((coi, idx) => {
                        return([
                            <Typography key={'coi-' + idx}>{formatName(coi.lastname, coi.firstname, coi.middleinitial) + ': ' +coi.institutionname}</Typography>
                        ])
                    }) : 'N/A'}
                </Grid>
                <StyledGridFieldLabel item xs={2}>
                    <div>Funding Source(s): </div>
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {translateRawToDisplayValue(requestData.request.fundingsource, fundingSourceOptions, true)}
                </Grid>
                <StyledGridFieldLabel item xs={2}>
                    <div>Grant Number(s): </div>
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {requestData.request.grantnumber}
                </Grid>
            </Grid>

            <p />
            <h4 style={{marginTop: 20}}>Institutional Animal Facilities and Capabilities</h4>
            <Grid container spacing={1}  style={{marginLeft: 10}}>
                <StyledGridFieldLabel item xs={2}>
                    Has Existing NHP Facilities:
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {translateRawToDisplayValue(requestData.request.existingnhpfacilities, existingNHPFacilityOptions)}
                </Grid>
                <StyledGridFieldLabel item xs={2}>
                    Has Existing Marmoset Colony:
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {translateRawToDisplayValue(requestData.request.existingmarmosetcolony, existingMarmosetColonyOptions)}
                </Grid>
                <StyledGridFieldLabel item xs={2}>
                    Plans to Breed Marmosets:
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {requestData.request.breedinganimals || ''}
                </Grid>
                <StyledGridFieldLabel item xs={2}>
                    Breeding Purpose:
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {requestData.request.breedingpurpose || 'N/A'}
                </Grid>
            </Grid>

            <p />
            <h4 style={{marginTop: 20}}>Research Details</h4>
            <Grid container spacing={1}  style={{marginLeft: 10}}>
                <StyledGridFieldLabel item xs={2}>
                    Animal Cohorts:
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {requestData.cohorts.length ? (
                        <Table style={{display: "inline-block", padding: 5}}>
                            <TableHead><TableRow key={"cohorts-header"}>
                                <StyledTableHead>Number of Animals</StyledTableHead>
                                <StyledTableHead>Sex</StyledTableHead>
                                <StyledTableHead>Other Characteristics</StyledTableHead>
                            </TableRow>
                            </TableHead>
                            <TableBody style={{border: 1, borderColor: 'black'}}>
                            {requestData.cohorts.map((cohort, idx) => {
                                return(
                                    <TableRow key={cohort.rowid} style = { idx % 2 ? { background : "#fdffe0" }:{ background : "white" }}>
                                        <StyledTableCell>{cohort.numberofanimals}</StyledTableCell>
                                        <StyledTableCell>{cohort.sex}</StyledTableCell>
                                        <StyledTableCell>{cohort.othercharacteristics}</StyledTableCell>
                                    </TableRow>
                                )
                            })}
                            </TableBody>
                            </Table>
                    ) : 'No cohorts entered'}
                </Grid>
                <StyledGridFieldLabel item xs={2}>
                    Methods Proposed:
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {requestData.request.methodsproposed}
                </Grid>
                <StyledGridFieldLabel item xs={2}>
                    Includes Terminal Procedures:
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {requestData.request.terminalprocedures ? 'Yes' : 'No'}
                </Grid>
                <StyledGridFieldLabel item xs={2}>
                    Collaborations:
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {requestData.request.collaborations || 'N/A'}
                </Grid>
                <StyledGridFieldLabel item xs={2}>
                    Animal Welfare:
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {requestData.request.animalwelfare}
                </Grid>
                <StyledGridFieldLabel item xs={2}>
                Attending Veterinarian:
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {requestData.request.vetlastname ? (requestData.request.vetlastname + (requestData.request.vetfirstname ? ', ' + requestData.request.vetfirstname : '')) : ''}
                    {requestData.request.vetemail ? ' (' + requestData.request.vetemail + ')' : ''}
                </Grid>
                <StyledGridFieldLabel item xs={2}>
                    IACUC Approval:
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {translateRawToDisplayValue(requestData.request.iacucapproval, IACUCApprovalOptions)}
                    {requestData.request.iacucprotocol ? ' (' + requestData.request.iacucprotocol + ')' : ''}
                </Grid>
                <StyledGridFieldLabel item xs={2}>
                    Participate In MCC Census:
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {requestData.request.census ? 'Yes' : 'No'}
                </Grid>
                <StyledGridFieldLabel item xs={2}>
                    Reason for not participating:
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {requestData.request.censusreason || 'N/A'}
                </Grid>
                <StyledGridFieldLabel item xs={2}>
                    Other Comments:
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {requestData.request.comments}
                </Grid>
                <StyledGridFieldLabel item xs={2}>
                    Status:
                </StyledGridFieldLabel>
                <Grid item xs={10}>
                    {requestData.request.status}
                </Grid>
            </Grid>
            <p />
            <Button variant={"contained"} style={{marginLeft: 10}} href={ActionURL.buildURL('mcc', 'animalRequest', null, {requestId: requestData.request.objectid})}>Edit Request</Button>
        </Box>
        {/*    TODO: more detail if authorized?*/}
        </>
    )
}