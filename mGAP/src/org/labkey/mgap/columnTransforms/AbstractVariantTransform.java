package org.labkey.mgap.columnTransforms;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
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
import org.labkey.api.util.PageFlowUtil;

import java.io.File;
import java.net.URI;
import java.sql.SQLException;
import java.util.Arrays;
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

    protected Integer getLibraryId()
    {
        String name = (String)getInputValue("genomeId/Name");
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
            URI uri = new URI(String.valueOf(dataFileUrl));
            File f = new File(uri);
            if (!f.exists())
            {
                getStatusLogger().error("File not found: " + uri.toString());
                return null;
            }
            else
            {
                PipeRoot pr = PipelineService.get().getPipelineRootSetting(getContainerUser().getContainer());
                String folderNameString = StringUtils.trimToNull(String.valueOf(folderName));
                if (folderNameString == null)
                {
                    throw new PipelineJobException("Unable to find folderName");
                }

                File subdir = new File(pr.getRootPath(), folderNameString);
                if (!subdir.exists())
                {
                    subdir.mkdirs();
                }

                //Copy file locally, plus index if exists:
                File localCopy = new File(subdir, f.getName());
                if (!localCopy.exists())
                {
                    getStatusLogger().info("copying file locally: " + localCopy.getPath());
                    FileUtils.copyFile(f, localCopy);
                }
                else
                {
                    getStatusLogger().info("file exists: " + localCopy.getPath());
                }

                File index = new File(f.getPath() + ".tbi");
                if (index.exists())
                {
                    File indexLocal = new File(subdir, index.getName());
                    if (!indexLocal.exists())
                    {
                        getStatusLogger().info("copying index locally: " + indexLocal.getPath());
                        FileUtils.copyFile(index, indexLocal);
                    }
                }

                //first create the ExpData
                ExpData d = ExperimentService.get().getExpDataByURL(localCopy, getContainerUser().getContainer());
                if (d == null)
                {
                    d = ExperimentService.get().createData(getContainerUser().getContainer(), new DataType("Variant Catalog"));
                    d.setDataFileURI(localCopy.toURI());
                    d.setName(f.getName());
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
                    row.put("category", "VCF File");
                    row.put("dataid", d.getRowId());
                    row.put("name", name == null ? "mGAP Variants, Version: " + getInputValue("version") : name);
                    row.put("description", "mGAP Release");
                    row.put("library_id", getLibraryId());
                    row.put("container", getContainerUser().getContainer().getId());
                    row.put("created", new Date());
                    row.put("createdby", getContainerUser().getUser().getUserId());
                    row.put("modified", new Date());
                    row.put("modifiedby", getContainerUser().getUser().getUserId());

                    List<Map<String, Object>> rows = getOutputFilesTableInfo().getUpdateService().insertRows(getContainerUser().getUser(), getContainerUser().getContainer(), Arrays.asList(row), new BatchValidationException(), null, new HashMap<>());
                    getStatusLogger().info("created outputfile: " + rows.get(0).get("rowid"));

                    return (Integer)rows.get(0).get("rowid");
                }
            }
        }
        catch (Exception e)
        {
            getStatusLogger().error("Error syncing file: " + String.valueOf(dataFileUrl), e);
        }

        return null;
    }
}
