package org.labkey.mgap.columnTransforms;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.jbrowse.JBrowseService;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.mgap.etl.EtlQueueManager;

import java.io.File;

public class LuceneIndexTransform extends OutputFileTransform
{
    @Override
    protected Object doTransform(Object inputValue)
    {
        if (null == inputValue)
            return null;

        return getOrCreateOutputFile(inputValue, getInputValue("objectId"), null);
    }

    @Override
    protected File doFileCopy(File f, File subdir, @Nullable String name) throws PipelineJobException
    {
        return doLuceneCopy(f, subdir, name, getStatusLogger(), getContainerUser().getContainer());
    }

    public static File doLuceneCopy(File f, File subdir, @Nullable String name, Logger log, Container container) throws PipelineJobException
    {
        log.info("preparing to copy lucene index: " + f.getPath() + ", with name: " + name);

        // NOTE: lucene is a special case since the DB tracks one file, but we need this whole folder:
        File sourceDir = f.getParentFile();
        File targetDir = new File(subdir, "LuceneIndex");
        JBrowseService.get().clearLuceneCacheEntry(targetDir);
        EtlQueueManager.get().queueRsyncCopy(container, sourceDir, targetDir);

        return new File(targetDir, sourceDir.getName() + "/" + f.getName());
    }

    @Override
    protected String getDescription()
    {
        return "mGAP Release Lucene Index";
    }
}
