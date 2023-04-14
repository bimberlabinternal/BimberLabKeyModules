import React from 'react';
import { AnimalRequestModel, AnimalRequestProps } from '../../components/RequestUtils';
import {
    existingMarmosetColonyOptions,
    existingNHPFacilityOptions,
    IACUCApprovalOptions,
    institutionTypeOptions,
    researchAreaOptions
} from '../../AnimalRequest/components/values';
import {
    Box,
    Button,
    Grid,
    makeStyles,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableRow,
    Typography
} from '@material-ui/core';
import { ActionURL } from '@labkey/api';

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
export default function ReadOnlyRequest(props: {requestData: AnimalRequestModel}) {
    const { requestData } = props
    const styles = makeStyles({
        fieldLabel: {
            fontSize: "14px",
            fontWeight: "bold"
        },
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

    if (!requestData?.dataLoaded) {
        return null
    }

    return(
        <>
        <h2>Request Details</h2>
        <Box display={"inline-block"} style={{marginBottom: 30}}>
            <h4 style={{marginTop: 10}}>Overview</h4>
            <Grid container spacing={1} style={{marginLeft: 10}}>
                <Grid item xs={2} className={styles.fieldLabel}>
                    Project Title:
                </Grid>
                <Grid item xs={10}>
                    {requestData.request.title}
                </Grid>
                <Grid item xs={2} className={styles.fieldLabel}>
                    Project Narrative:
                </Grid>
                <Grid item xs={10}>
                    {requestData.request.narrative}
                </Grid>
                <Grid item xs={2} className={styles.fieldLabel}>
                    Research/disease focus:
                </Grid>
                <Grid item xs={10}>
                    {requestData.request.diseasefocus}
                </Grid>
                <Grid item xs={2} className={styles.fieldLabel}>
                    Relation to neuroscience:
                </Grid>
                <Grid item xs={10}>
                    {requestData.request.neuroscience}
                </Grid>
            </Grid>
            <h4 style={{marginTop: 10}}>General Information</h4>
            <Grid container spacing={1} style={{marginLeft: 10}}>
                <Grid item xs={2} className={styles.fieldLabel}>
                    PI Name:
                </Grid>
                <Grid item xs={10}>
                    {formatPIName(requestData.request)}
                </Grid>
                <Grid item xs={2} className={styles.fieldLabel}>
                    Early State Investigator:
                </Grid>
                <Grid item xs={10}>
                    {requestData.request.earlystageinvestigator ? 'Yes' : 'No'}
                </Grid>
                <Grid item xs={2} className={styles.fieldLabel}>
                    <div>Institution: </div>
                </Grid>
                <Grid item xs={10}>
                    {requestData.request.institutionname || ''}
                </Grid>
                <Grid item xs={2} className={styles.fieldLabel}>
                    <div>Institution City: </div>
                </Grid>
                <Grid item xs={10}>
                    {requestData.request.institutioncity}, {requestData.request.institutionstate} {requestData.request.institutioncountry}
                </Grid>
                <Grid item xs={2} className={styles.fieldLabel}>
                    <div>Institution Type: </div>
                </Grid>
                <Grid item xs={10}>
                    {translateRawToDisplayValue(requestData.request.institutiontype, institutionTypeOptions)}
                </Grid>
                <Grid item xs={2} className={styles.fieldLabel}>
                    <div>Signing Official: </div>
                </Grid>
                <Grid item xs={10}>
                    {formatName(requestData.request.officiallastname, requestData.request.officialfirstname, null)}
                    {requestData.request.officialemail ? ' (' + requestData.request.officialemail + ')' : ''}
                </Grid>
                <Grid item xs={2} className={styles.fieldLabel}>
                    <div>Co-Investigators: </div>
                </Grid>
                <Grid item xs={10}>
                    {requestData.coinvestigators.length ? requestData.coinvestigators.map((coi, idx) => {
                        return([
                            <Typography key={'coi-' + idx}>{formatName(coi.lastname, coi.firstname, coi.middleinitial) + ': ' +coi.institutionname}</Typography>
                        ])
                    }) : 'N/A'}
                </Grid>
            </Grid>

            <p />
            <h4 style={{marginTop: 20}}>Institutional Animal Facilities and Capabilities</h4>
            <Grid container spacing={1}  style={{marginLeft: 10}}>
                <Grid item xs={2} className={styles.fieldLabel}>
                    Has Existing NHP Facilities:
                </Grid>
                <Grid item xs={10}>
                    {translateRawToDisplayValue(requestData.request.existingnhpfacilities, existingNHPFacilityOptions)}
                </Grid>
                <Grid item xs={2} className={styles.fieldLabel}>
                    Has Existing Marmoset Colony:
                </Grid>
                <Grid item xs={10}>
                    {translateRawToDisplayValue(requestData.request.existingmarmosetcolony, existingMarmosetColonyOptions)}
                </Grid>
                <Grid item xs={2} className={styles.fieldLabel}>
                    Plans to Breed Marmosets:
                </Grid>
                <Grid item xs={10}>
                    {requestData.request.breedinganimals || ''}
                </Grid>
                <Grid item xs={2} className={styles.fieldLabel}>
                    Breeding Purpose:
                </Grid>
                <Grid item xs={10}>
                    {requestData.request.breedingpurpose || 'N/A'}
                </Grid>
            </Grid>

            <p />
            <h4 style={{marginTop: 20}}>Research Details</h4>
            <Grid container spacing={1}  style={{marginLeft: 10}}>
                <Grid item xs={2} className={styles.fieldLabel}>
                    Animal Cohorts:
                </Grid>
                <Grid item xs={10}>
                    {requestData.cohorts.length ? (
                        <Table style={{display: "inline-block", padding: 5}}>
                            <TableHead><TableRow key={"cohorts-header"}>
                                <TableCell className={styles.tableHead}>Number of Animals</TableCell><TableCell className={styles.tableHead}>Sex</TableCell><TableCell className={styles.tableHead}>Other Characteristics</TableCell>
                            </TableRow>
                            </TableHead>
                            <TableBody style={{border: 1, borderColor: 'black'}}>
                            {requestData.cohorts.map((cohort, idx) => {
                                return(
                                    <TableRow key={cohort.rowid} style = { idx % 2 ? { background : "#fdffe0" }:{ background : "white" }}>
                                        <TableCell className={styles.tableCell}>{cohort.numberofanimals}</TableCell>
                                        <TableCell className={styles.tableCell}>{cohort.sex}</TableCell>
                                        <TableCell className={styles.tableCell}>{cohort.othercharacteristics}</TableCell>
                                    </TableRow>
                                )
                            })}
                            </TableBody>
                            </Table>
                    ) : 'No cohorts entered'}
                </Grid>
                <Grid item xs={2} className={styles.fieldLabel}>
                    Methods Proposed:
                </Grid>
                <Grid item xs={10}>
                    {requestData.request.methodsproposed}
                </Grid>
                <Grid item xs={2} className={styles.fieldLabel}>
                    Includes Terminal Procedures:
                </Grid>
                <Grid item xs={10}>
                    {requestData.request.terminalprocedures ? 'Yes' : 'No'}
                </Grid>
                <Grid item xs={2} className={styles.fieldLabel}>
                    Collaborations:
                </Grid>
                <Grid item xs={10}>
                    {requestData.request.collaborations || 'N/A'}
                </Grid>
                <Grid item xs={2} className={styles.fieldLabel}>
                    Animal Welfare:
                </Grid>
                <Grid item xs={10}>
                    {requestData.request.animalwelfare}
                </Grid>
                <Grid item xs={2} className={styles.fieldLabel}>
                Attending Veterinarian:
                </Grid>
                <Grid item xs={10}>
                    {requestData.request.vetlastname ? (requestData.request.vetlastname + (requestData.request.vetfirstname ? ', ' + requestData.request.vetfirstname : '')) : ''}
                    {requestData.request.vetemail ? ' (' + requestData.request.vetemail + ')' : ''}
                </Grid>
                <Grid item xs={2} className={styles.fieldLabel}>
                    IACUC Approval:
                </Grid>
                <Grid item xs={10}>
                    {translateRawToDisplayValue(requestData.request.iacucapproval, IACUCApprovalOptions)}
                    {requestData.request.iacucprotocol ? ' (' + requestData.request.iacucprotocol + ')' : ''}
                </Grid>
                <Grid item xs={2} className={styles.fieldLabel}>
                    Participate In MCC Census:
                </Grid>
                <Grid item xs={10}>
                    {requestData.request.census ? 'Yes' : 'No'}
                </Grid>
                <Grid item xs={2} className={styles.fieldLabel}>
                    Reason for not participating:
                </Grid>
                <Grid item xs={10}>
                    {requestData.request.censusreason || 'N/A'}
                </Grid>
                <Grid item xs={2} className={styles.fieldLabel}>
                    Status:
                </Grid>
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