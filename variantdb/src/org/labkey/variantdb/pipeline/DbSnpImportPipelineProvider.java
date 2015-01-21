package org.labkey.variantdb.pipeline;

import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.view.ViewContext;

/**
 * Created by bimber on 1/5/2015.
 */
public class DbSnpImportPipelineProvider extends PipelineProvider
{
    public static final String NAME = "dbSNPPipeline";
    public static final String URL_BASE = "ftp://ftp.ncbi.nlm.nih.gov/snp/organisms";

    public DbSnpImportPipelineProvider(Module owningModule)
    {
        super(NAME, owningModule);
    }

    @Override
    public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
    {

    }
}
