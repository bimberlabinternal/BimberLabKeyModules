package org.labkey.mgap.etl;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EtlQueueManager
{
    private static EtlQueueManager _instance = new EtlQueueManager();

    private Map<Container, List<Pair<File, File>>> _pendingFileCopy = new HashMap<>();

    private Map<Container, List<Pair<File, File>>> _pendingRsyncCopy = new HashMap<>();

    private EtlQueueManager()
    {

    }

    public static EtlQueueManager get()
    {
        return _instance;
    }

    public void clearQueue(Container container, Logger log)
    {
        if (_pendingFileCopy.containsKey(container) && !_pendingFileCopy.get(container).isEmpty())
        {
            log.error("The file copy queue was not empty!");
        }

        if (_pendingRsyncCopy.containsKey(container) && !_pendingRsyncCopy.get(container).isEmpty())
        {
            log.error("The rsync copy queue was not empty!");
        }

        _pendingFileCopy.clear();
        _pendingRsyncCopy.clear();
    }

    public void performQueuedWork(Container container, Logger log)
    {
        List<Pair<File, File>> queue = _pendingFileCopy.get(container);
        if (queue != null && !queue.isEmpty())
        {
            queue.forEach(x -> copyFile(x.getLeft(), x.getRight(), log));
        }
        _pendingFileCopy.clear();

        List<Pair<File, File>> rsyncQueue = _pendingRsyncCopy.get(container);
        if (rsyncQueue != null && !rsyncQueue.isEmpty())
        {
            rsyncQueue.forEach(x -> doRsyncCopy(x.getLeft(), x.getRight(), log));
        }
        _pendingRsyncCopy.clear();
    }

    public void queueFileCopy(Container c, File source, File destination)
    {
        if (!_pendingFileCopy.containsKey(c))
        {
            _pendingFileCopy.put(c, new ArrayList<>());
        }

        _pendingFileCopy.get(c).add(Pair.of(source, destination));
    }

    public void queueRsyncCopy(Container c, File source, File destination)
    {
        if (!_pendingRsyncCopy.containsKey(c))
        {
            _pendingRsyncCopy.put(c, new ArrayList<>());
        }

        _pendingRsyncCopy.get(c).add(Pair.of(source, destination));
    }

    private void doRsyncCopy(File sourceDir, File destination, Logger log)
    {
        // NOTE: rsync should no-op if there are no source changes
        log.info("Performing rsync from: " + sourceDir.getPath() + " to " + destination.getPath());
        try
        {
            new SimpleScriptWrapper(log).execute(Arrays.asList(
                    "rsync", "-r", "-a", "--delete", "--no-owner", "--no-group", "--chmod=D2770,F660", sourceDir.getPath(), destination.getPath()
            ));
        }
        catch (PipelineJobException e)
        {
            log.error("Error running rsync", e);
        }
    }

    private void copyFile(File source, File destination, Logger log)
    {
        if (destination.exists())
        {
            destination.delete();
        }

        try
        {
            log.info("Copying file: " + source.getPath());
            FileUtils.copyFile(source, destination);
        }
        catch (IOException e)
        {
            log.error("Error copying file", e);
        }
    }
}
