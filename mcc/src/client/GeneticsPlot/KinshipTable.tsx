import React from 'react';
import { DataGrid, GridColumns, GridToolbar } from '@mui/x-data-grid';

export default function KinshipTable(props: {data: any}) {
    const { data } = props;
    const [pageSize, setPageSize] = React.useState<number>(25);

    const columns: GridColumns = [
        { field: 'Id', headerName: 'Animal 1', width: 150, type: "string", headerAlign: 'left' },
        { field: 'Id2', headerName: 'Animal 2', width: 150, type: "string", headerAlign: 'left' },
        { field: 'kinship', headerName: 'Kinship', width: 125, type: "number", headerAlign: 'right' },
        { field: 'relationship', headerName: 'Inferred Relationship', width: 200, type: "string", headerAlign: 'left' }
    ]

    return (
        <DataGrid
            autoHeight={true}
            columns={columns}
            rows={data}
            components={{ Toolbar: GridToolbar }}
            rowsPerPageOptions={[10,25,50,100]}
            pageSize={pageSize}
            onPageSizeChange={(newPageSize) => setPageSize(newPageSize)}
        />
    );
}