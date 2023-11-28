package org.labkey.mgap.jbrowse;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.jbrowse.JBrowseService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.mgap.mGAPModule;
import org.labkey.mgap.mGAPSchema;

import java.util.List;

public class mGAPLuceneDetector implements JBrowseService.LuceneIndexDetector
{
    @Override
    public SequenceOutputFile findMatchingLuceneIndex(SequenceOutputFile vcfFile, List<String> infoFieldsToIndex, User u, @Nullable Logger log) throws PipelineJobException
    {
        Container target = vcfFile.getContainerObj().isWorkbookOrTab() ? vcfFile.getContainerObj().getParent() : vcfFile.getContainerObj();
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("vcfId"), vcfFile.getRowid());
        filter.addCondition(FieldKey.fromString("luceneIndex"), null, CompareType.NONBLANK);

        TableSelector ts = new TableSelector(QueryService.get().getUserSchema(u, target, mGAPSchema.NAME).getTable(mGAPSchema.TABLE_VARIANT_CATALOG_RELEASES), PageFlowUtil.set("luceneIndex"), filter, null);
        if (ts.exists())
        {
            return SequenceOutputFile.getForId(ts.getObject(Integer.class));
        }

        return null;
    }

    @Override
    public boolean isAvailable(Container c)
    {
        return c.getActiveModules().contains(ModuleLoader.getInstance().getModule(mGAPModule.class));
    }
}
