import React from 'react';
import { DataGrid, GridColDef, GridPaginationModel, GridToolbar } from '@mui/x-data-grid';

export default function KinshipTable(props: {data: any}) {
    const { data } = props;
    const [pageModel, setPageModel] = React.useState<GridPaginationModel>({page: 0, pageSize: 25});

    const columns: GridColDef[] = [
        { field: 'Id', headerName: 'Animal 1', width: 150, type: "string", headerAlign: 'left' },
        { field: 'Id2', headerName: 'Animal 2', width: 150, type: "string", headerAlign: 'left' },
        { field: 'kinship', headerName: 'Kinship', width: 125, type: "number", headerAlign: 'right' },
        { field: 'relationship', headerName: 'Inferred Relationship', width: 200, type: "string", headerAlign: 'left', flex: 1 }
    ]

    return (
        <DataGrid
            autoHeight={true}
            columns={columns}
            rows={data}
            // slots: {{
            //     toolbar: GridToolbar
            // }}
            pageSizeOptions={[10,25,50,100]}
            paginationModel={pageModel}
            onPaginationModelChange={(model) => setPageModel(model)}
        />
    );
}