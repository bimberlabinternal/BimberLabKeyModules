package org.labkey.mgap.columnTransforms;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

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
        // NOTE: lucene is a special case since the DB tracks one file, but we need this whole folder:
        File sourceDir = f.getParentFile();
        File targetDir = new File(subdir, "LuceneIndex");

        // NOTE: rsync should no-op if there are no source changes
        getStatusLogger().info("Copying lucene index dir to: " + targetDir.getPath());
        new SimpleScriptWrapper(getStatusLogger()).execute(Arrays.asList(
                "rsync", "-r", "-a", "--delete", "--no-owner", "--no-group", "--chmod=D2770,F660", sourceDir.getPath(), targetDir.getPath()
        ));

        return new File(targetDir, sourceDir.getName() + "/" + f.getName());
    }

    @Override
    protected String getDescription()
    {
        return "mGAP Release Lucene Index";
    }
}
