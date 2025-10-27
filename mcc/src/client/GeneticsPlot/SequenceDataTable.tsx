import React from 'react';
import { DataGrid, GridColDef, GridPaginationModel, GridRenderCellParams, GridToolbar } from '@mui/x-data-grid';

export default function SequenceDataTable(props: {data: any}) {
    const { data } = props;
    const [pageModel, setPageModel] = React.useState<GridPaginationModel>({page: 0, pageSize: 25});

    const columns: GridColDef[] = [
        { field: 'Id', headerName: 'Animal 1', width: 150, type: "string", headerAlign: 'left' },
        { field: 'datatype', headerName: 'Datatype', width: 250, type: "string", headerAlign: 'left' },
        { field: 'sra_accession', headerName: 'SRA Accession', width: 150, type: "string", headerAlign: 'right', renderCell: (params: GridRenderCellParams<any, string>) => {
                return (
                    <a
                        target="_blank"
                        href={params.value ? "https://trace.ncbi.nlm.nih.gov/Traces/sra/?run=" + params.value : ""}
                    >{params.value}</a>
                );
            }},
        { field: 'total_reads', headerName: 'Total Reads', width: 125, type: "number", headerAlign: 'left', flex: 1 }
    ]

    return (
        <>
        <div style={{paddingBottom: 20, maxWidth: 1000}}>
        </div>
        <DataGrid
            autoHeight={true}
            columns={columns}
            rows={data}
            slots={{
                toolbar: GridToolbar
            }}
            pageSizeOptions={[10,25,50,100]}
            paginationModel={pageModel}
            onPaginationModelChange={(model) => setPageModel(model)}
        />
        </>
    );
}