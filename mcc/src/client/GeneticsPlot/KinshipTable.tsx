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
        <>
        <div style={{paddingBottom: 20, maxWidth: 1000}}>
            Although PCA is useful for broad-scale comparisons, it is not very useful when trying to distinguish
            whether two individuals are siblings or first-cousins, for instance. For that, we have better statistics
            that can describe the genetic relatedness between two individuals. We estimated genetic relatedness for
            all pairs of individuals for which we have whole-genome data, shown in the table below. There you will
            find the inferred relationships between pairs of individuals as well as the calculated kinship coefficient,
            which is a quantitative measure of genetic relatedness
            (see <a href="https://en.wikipedia.org/wiki/Coefficient_of_relationship#Kinship_coefficient">here</a> for more details).
        </div>
        <DataGrid
            autoHeight={true}
            columns={columns}
            rows={data}
            showToolbar
            slots={{
                toolbar: GridToolbar
            }}
            slotProps={{
                toolbar: { sx: { justifyContent: 'flex-start' } }
            }}
            pageSizeOptions={[10,25,50,100]}
            paginationModel={pageModel}
            onPaginationModelChange={(model) => setPageModel(model)}
        />
        </>
    );
}