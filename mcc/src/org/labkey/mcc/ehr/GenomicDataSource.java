package org.labkey.mcc.ehr;

import org.labkey.api.data.Container;
import org.labkey.api.data.Results;
import org.labkey.api.ehr.history.AbstractDataSource;
import org.labkey.api.module.Module;

import java.sql.SQLException;

/**
 * User: bimber
 * Date: 2/17/13
 * Time: 4:52 PM
 */
public class GenomicDataSource extends AbstractDataSource
{
    public GenomicDataSource(Module module)
    {
        super("study", "genomicDatasets", "Genomics", "Genomics", module);
    }

    @Override
    protected String getHtml(Container c, Results rs, boolean redacted) throws SQLException
    {
        StringBuilder sb = new StringBuilder();


        sb.append(safeAppend(rs, "Datatype", "datatype"));
        sb.append(safeAppend(rs, "SRA Accesson", "sra_accession"));

        return sb.toString();
    }
}
