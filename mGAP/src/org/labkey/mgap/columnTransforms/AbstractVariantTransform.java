package org.labkey.mgap.columnTransforms;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Results;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.StopIteratingException;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.di.columnTransform.ColumnTransform;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.mgap.etl.EtlQueueManager;
import org.labkey.mgap.mGAPManager;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 5/15/2017.
 */
abstract public class AbstractVariantTransform extends ColumnTransform
{
    private transient TableInfo _outputFilesTableInfo = null;
    private transient Map<String, Integer> _genomeIdMap = null;

    @Override
    public void reset()
    {
        _outputFilesTableInfo = null;
        _genomeIdMap = null;
    }

    protected Map<String, Integer> getGenomeIdMap()
    {
        if (_genomeIdMap == null)
        {
            _genomeIdMap = getGenomeMap();
        }

        return _genomeIdMap;
    }

    private Map<String, Integer> getGenomeMap()
    {
        TableInfo ti = QueryService.get().getUserSchema(getContainerUser().getUser(), getContainerUser().getContainer(), "sequenceanalysis").getTable("reference_libraries");
        final Map<String, Integer> genomeMap = new HashMap<>();
        new TableSelector(ti, PageFlowUtil.set("rowid", "name")).forEachResults(new Selector.ForEachBlock<Results>()
        {
            @Override
            public void exec(Results rs) throws SQLException, StopIteratingException
            {
                genomeMap.put(rs.getString("name"), rs.getInt("rowid"));
            }
        });

        return genomeMap;
    }

    protected TableInfo getOutputFilesTableInfo()
    {
        if (_outputFilesTableInfo == null)
        {
            _outputFilesTableInfo = QueryService.get().getUserSchema(getContainerUser().getUser(), getContainerUser().getContainer(), "sequenceanalysis").getTable("outputfiles");
        }

        return _outputFilesTableInfo;
    }

    protected String getGenomeIdField()
    {
        return "genomeId/Name";
    }

    protected Integer getLibraryId()
    {
        String name = (String)getInputValue(getGenomeIdField());
        Map<String, Integer> genomeMap = getGenomeIdMap();
        if (name == null || genomeMap == null)
        {
            return null;
        }

        return genomeMap.get(name);
    }

    protected Integer getOrCreateOutputFile(Object dataFileUrl, Object folderName, String name)
    {
        try
        {
            if (dataFileUrl == null)
            {
                throw new IllegalArgumentException("DataFileUrl was null");
            }

            URI uri = new URI(String.valueOf(dataFileUrl));
            File f = new File(uri);
            if (!f.exists())
            {
                if (!EtlQueueManager.get().isFileInQueue(getContainerUser().getContainer(), f))
                {
                    getStatusLogger().error("File not found: " + uri);
                    return null;
                }
                else
                {
                    getStatusLogger().debug("File is in ETL queue: " + f.getPath());
                }
            }

            File subDir = getLocalSubdir(folderName);
            File localCopy = doFileCopy(f, subDir, name);
            if (localCopy == null)
            {
                getStatusLogger().warn("localCopy was null", new Exception());
            }

            //first create the ExpData
            ExpData d = ExperimentService.get().getExpDataByURL(localCopy, getContainerUser().getContainer());
            if (d == null)
            {
                d = ExperimentService.get().createData(getContainerUser().getContainer(), new DataType("Variant Catalog"));
                d.setDataFileURI(localCopy.toURI());
                d.setName(localCopy.getName());
                d.save(getContainerUser().getUser());
            }

            //then the outputfile
            TableSelector ts = new TableSelector(getOutputFilesTableInfo(), PageFlowUtil.set("rowid"), new SimpleFilter(FieldKey.fromString("dataId"), d.getRowId()), null);
            if (ts.exists())
            {
                getStatusLogger().info("existing record found for outputfile: " + d.getDataFileUrl());
                return ts.getObject(Integer.class);
            }
            else
            {
                Map<String, Object> row = new CaseInsensitiveHashMap<>();
                row.put("category", getOutputFileCategory());
                row.put("dataid", d.getRowId());
                row.put("name", name == null ? "mGAP Variants, Version: " + getInputValue("version") : name);
                row.put("description", getDescription());
                row.put("library_id", getLibraryId());
                row.put("container", getContainerUser().getContainer().getId());
                row.put("created", new Date());
                row.put("createdby", getContainerUser().getUser().getUserId());
                row.put("modified", new Date());
                row.put("modifiedby", getContainerUser().getUser().getUserId());

                List<Map<String, Object>> rows = getOutputFilesTableInfo().getUpdateService().insertRows(getContainerUser().getUser(), getContainerUser().getContainer(), List.of(row), new BatchValidationException(), null, new HashMap<>());
                getStatusLogger().info("created outputfile: " + rows.get(0).get("rowid"));

                return (Integer)rows.get(0).get("rowid");
            }
        }
        catch (Exception e)
        {
            getStatusLogger().error("Error syncing file: " + dataFileUrl, e);
        }

        return null;
    }

    protected File getLocalSubdir(Object folderName) throws PipelineJobException
    {
        PipeRoot pr = PipelineService.get().getPipelineRootSetting(getContainerUser().getContainer());
        File baseDir = FileUtil.appendName(pr.getRootPath(), mGAPManager.DATA_DIR_NAME);
        if (!baseDir.exists())
        {
            baseDir.mkdirs();
        }

        String folderNameString = StringUtils.trimToNull(String.valueOf(folderName));
        if (folderNameString == null)
        {
            throw new PipelineJobException("Unable to find folderName");
        }

        File subdir = FileUtil.appendName(baseDir, folderNameString);
        if (!subdir.exists())
        {
            subdir.mkdirs();
        }

        return subdir;
    }

    protected File doFileCopy(File f, File subdir, @Nullable String name) throws PipelineJobException
    {
        getStatusLogger().info("preparing to copy file: " + f.getPath() + ", with name: " + name);
        if (f.getName().equals("write.lock"))
        {
            return LuceneIndexTransform.doLuceneCopy(f, subdir, name, getStatusLogger(), getContainerUser().getContainer());
        }

        //Copy file locally, plus index if exists:
        File localCopy = FileUtil.appendName(subdir, name == null || f.getName().startsWith("mGap.v") ? f.getName() : FileUtil.makeLegalName(name).replaceAll(" ", "_") + ".vcf.gz");
        if (f.equals(localCopy))
        {
            return localCopy;
        }

        boolean doCopy = true;
        if (localCopy.exists())
        {
            getStatusLogger().info("file exists: " + localCopy.getPath());
            if (localCopy.lastModified() >= f.lastModified())
            {
                doCopy = false;
            }
            else
            {
                getStatusLogger().info("source file has been modified, deleting copy and re-syncing: " + localCopy.getPath());
                try
                {
                    Files.delete(localCopy.toPath());
                    if (localCopy.exists())
                    {
                        throw new PipelineJobException("Unable to delete file: " + localCopy.getPath());
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException("Unable to delete file: " + localCopy.getPath(), e);
                }
            }
        }
        else
        {
            getStatusLogger().info("existing file not found: " + localCopy.getPath());
        }

        if (doCopy)
        {
            getStatusLogger().info("Creating local copy of file: " + f.getPath() + " / " + localCopy.getPath());
            try
            {
                if (!Files.isReadable(f.toPath()))
                {
                    throw new PipelineJobException("Unable to read file: " + f.getPath());
                }

                if (localCopy.exists())
                {
                    throw new PipelineJobException("File should have been deleted: " + localCopy.getPath());
                }

                Files.copy(f.toPath(), localCopy.toPath());
            }
            catch (IOException e)
            {
                throw new PipelineJobException("Failed to create local copy: " + localCopy.getPath(), e);
            }
        }

        File index = new File(f.getPath() + ".tbi");
        if (index.exists())
        {
            File indexLocal = new File(localCopy.getPath() + ".tbi");
            if (doCopy && indexLocal.exists())
            {
                getStatusLogger().info("deleting local copy of index since file will be re-copied: " + indexLocal.getPath());
                indexLocal.delete();
            }

            if (!indexLocal.exists())
            {
                getStatusLogger().info("Creating local copy of VCF index: " + index.getPath() + " / " + indexLocal.getPath());
                try
                {
                    Files.copy(index.toPath(), indexLocal.toPath());
                }
                catch (IOException e)
                {
                    getStatusLogger().error("Failed to create local copy: " + indexLocal.getPath(), e);
                }
            }
            else
            {
                getStatusLogger().info("Local copy of index already exists: " + indexLocal.getPath());
            }
        }

        return localCopy;
    }

    protected String getOutputFileCategory()
    {
        return "VCF File";
    }

    protected String getDescription()
    {
        return "mGAP Release";
    }
}
