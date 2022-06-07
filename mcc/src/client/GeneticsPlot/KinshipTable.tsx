import React from 'react';
import { DataGrid, GridColumns, GridToolbar } from '@mui/x-data-grid';

export default function KinshipTable(props: {data: any}) {
    const { data } = props;
    const [pageSize, setPageSize] = React.useState<number>(25);

    const columns: GridColumns = [
        { field: 'Id', headerName: 'Animal 1', width: 100, type: "string", flex: 1, headerAlign: 'left' },
        { field: 'Id2', headerName: 'Animal 2', width: 100, type: "string", flex: 1, headerAlign: 'left' },
        { field: 'kinship', headerName: 'Kinship', width: 50, type: "number", flex: 1, headerAlign: 'left' },
        { field: 'relationship', headerName: 'Inferred Relationship', width: 100, type: "string", flex: 1, headerAlign: 'left' }
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