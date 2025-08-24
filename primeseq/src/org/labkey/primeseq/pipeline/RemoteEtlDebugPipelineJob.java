package org.labkey.primeseq.pipeline;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.assay.AssayFileWriter;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.WrapperDataIterator;
import org.labkey.api.di.DataIntegrationService;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.JSONDataLoader;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.primeseq.PrimeseqController;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.query.SelectRowsCommand;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

public class RemoteEtlDebugPipelineJob extends PipelineJob
{
    public static class Provider extends PipelineProvider
    {
        public static final String NAME = "etlDebugPipeline";

        public Provider(Module owningModule)
        {
            super(NAME, owningModule);
        }

        @Override
        public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
        {

        }
    }

    // Default constructor for serialization
    protected RemoteEtlDebugPipelineJob()
    {
    }

    private RemoteEtlDebugPipelineJob(String providerName, Container c, User u, PipeRoot pipeRoot)
    {
        super(providerName, new ViewBackgroundInfo(c, u, null), pipeRoot);
    }

    public static RemoteEtlDebugPipelineJob create(Container c, User u)
    {
        PipeRoot pipeRoot = PipelineService.get().getPipelineRootSetting(c);
        RemoteEtlDebugPipelineJob job = new RemoteEtlDebugPipelineJob(Provider.NAME, c, u, pipeRoot);
        job.setLogFile(AssayFileWriter.findUniqueFileName(FileUtil.makeLegalName("etlDebugJob.log"), pipeRoot.getRootPath()).toPath());

        return job;
    }

    @Override
    public URLHelper getStatusHref()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return "Designed to debug remote API calls and mimic ETLs";
    }

    @Override
    public void run()
    {
        try
        {
            setStatus(TaskStatus.running);
            getLogger().info("Starting");

            DataIntegrationService.RemoteConnection rc = DataIntegrationService.get().getRemoteConnection("PRIMe", getContainer(), _log);
            if (rc == null)
            {
                throw new IllegalStateException("Unable to find remote data source named PRIMe in this container");
            }

            getLogger().info("Querying remote server");

            SelectRowsCommand2 sr = new SelectRowsCommand2("study", "weight");
            sr.setColumns(Arrays.asList("Id", "date", "objectid", "QCState/Label"));

            final int BATCH_SIZE = 1000;
            final int PAUSE_LENGTH = 10;
            AtomicInteger al = new AtomicInteger();
            DataIterator di = sr.getWrapperDataIterator(rc.connection, rc.remoteContainer, getContainer(), getLogger());

            UserSchema us = QueryService.get().getUserSchema(getUser(), getContainer(), "study");
            if (us == null)
            {
                throw new IllegalStateException("Unable to find user schema for study");
            }

            TableInfo weights = us.getTable("weight");
            if (weights == null)
            {
                throw new IllegalStateException("Unable to find weights");
            }

            QueryUpdateService qus = weights.getUpdateService();
            qus.setBulkLoad(true);


            try (DbScope.Transaction transaction = DbScope.getLabKeyScope().ensureTransaction())
            {
                final Date lastValue = new Date();
                di.stream().forEach(row -> {
                    try
                    {
                        al.getAndIncrement();
                        qus.insertRows(getUser(), getContainer(), Arrays.asList(row), null, null, null);

                        if (al.get() % BATCH_SIZE == 0)
                        {
                            Date now = new Date();
                            long duration = now.getTime() - lastValue.getTime();
                            lastValue.setTime(now.getTime());
                            getLogger().info("Imported " + al.get() + " rows. Duration (including sleep): " + (duration/1000));

                            try
                            {
                                getLogger().info("Sleeping for " + PAUSE_LENGTH + " seconds.");
                                Thread.sleep(PAUSE_LENGTH * 1000);
                                getLogger().info("Done sleeping");
                            }
                            catch (InterruptedException e)
                            {
                                getLogger().error(e.getMessage(), e);
                            }
                        }
                    }
                    catch (Exception e)
                    {
                        getLogger().error(e.getMessage(), e);
                    }
                });
            }

            getLogger().info("Done!");
        }
        catch (Exception e)
        {
            setStatus(TaskStatus.error);
            throw new RuntimeException(e);
        }
    }

    // This is a hack to access SelectRowsStreamHack
    private static class SelectRowsCommand2 extends SelectRowsCommand
    {

        public SelectRowsCommand2(String schemaName, String queryName)
        {
            super(schemaName, queryName);
        }

        public WrapperDataIterator getWrapperDataIterator(Connection connection, String remoteContainer, Container localContainer, Logger logger)
        {
            Response response;
            try
            {
                response = _execute(connection, remoteContainer);
                logger.info("response headers:");
                for (String key : Arrays.asList("Connection", "Transfer-Encoding", "Cache-Control", "Keep-Alive", "Pragma"))
                {
                    logger.info(key + ": " + response.getHeaderValue(key));
                }
            }
            catch (CommandException | IOException e)
            {
                throw new RuntimeException("Failed to execute remote query", e);
            }

            try
            {
                final InputStream is = response.getInputStream();
                final JSONDataLoader loader = new JSONDataLoader(is, localContainer);
                DataIteratorContext di = new DataIteratorContext();
                WrapperDataIterator wrapper = new WrapperDataIterator(loader.getDataIterator(di))
                {
                    @Override
                    public void close() throws IOException
                    {
                        logger.info("Calling close() in WrapperDataIterator");

                        // close the InputStream and http connection
                        if (is != null)
                            IOUtils.closeQuietly(is);
                        response.close();

                        // close the JSONDataLoader
                        super.close();
                    }
                };
                wrapper.setDebugName("SelectRows:JSONDataLoader");

                return wrapper;
            }
            catch (Exception e)
            {
                logger.info(e.getMessage());
                logger.info("status code: " + response.getStatusCode());
                logger.error(e);

                throw new RuntimeException(e);
            }
        }
    }
}
