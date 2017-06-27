package org.labkey.mgap.columnTransforms;

import org.apache.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.jbrowse.JBrowseService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JobRunner;
import org.labkey.api.util.PageFlowUtil;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 5/15/2017.
 */
public class JBrowseSessionTransform extends AbstractVariantTransform
{
    private static final Logger _log = Logger.getLogger(JBrowseSessionTransform.class);

    private transient TableInfo _jsonFiles;
    private transient TableInfo _databaseMembers;
    private transient TableInfo _databases;
    private transient UserSchema _jbus;

    @Override
    protected Object doTransform(Object inputValue)
    {
        Integer outputFileId = getOrCreateOutputFile(getInputValue("vcfId/dataid/DataFileUrl"));
        if (outputFileId != null)
        {
            //determine if there is already a JSONfile
            String jsonFileId;
            TableSelector ts1 = new TableSelector(getJsonFiles(), PageFlowUtil.set("objectid"), new SimpleFilter(FieldKey.fromString("outputfile"), outputFileId), null);
            if (ts1.exists())
            {
                jsonFileId = ts1.getArrayList(String.class).get(0);
            }
            else
            {
                try
                {
                    //create
                    TableInfo jsonFiles = getJbrowseUserSchema().getTable("jsonfiles");
                    CaseInsensitiveHashMap<Object> row = new CaseInsensitiveHashMap<>();
                    row.put("objectid", new GUID().toString());
                    row.put("outputFile", outputFileId);
                    row.put("relPath", "tracks/data-" + outputFileId);
                    row.put("container", getContainerUser().getContainer().getId());
                    row.put("trackJson", "{\"visibleByDefault\": true}");
                    row.put("created", new Date());
                    row.put("createdby", getContainerUser().getUser().getUserId());
                    row.put("modified", new Date());
                    row.put("modifiedby", getContainerUser().getUser().getUserId());

                    List<Map<String, Object>> rows = jsonFiles.getUpdateService().insertRows(getContainerUser().getUser(), getContainerUser().getContainer(), Arrays.asList(row), new BatchValidationException(), null, new HashMap<>());
                    jsonFileId = (String)rows.get(0).get("objectid");
                }
                catch (Exception e)
                {
                    _log.error("Error creating jsonfile: " + String.valueOf(inputValue), e);
                    return null;
                }
            }

            //then find if used in database
            final String databaseId;
            TableSelector ts2 = new TableSelector(getDatabaseMembers(), PageFlowUtil.set("database"), new SimpleFilter(FieldKey.fromString("jsonfile"), jsonFileId), null);
            if (ts2.exists())
            {
                return ts2.getArrayList(String.class).get(0);
            }
            else
            {
                try
                {
                    //create database
                    TableInfo databases = getJbrowseUserSchema().getTable("databases");
                    CaseInsensitiveHashMap<Object> dbRow = new CaseInsensitiveHashMap<>();
                    databaseId = new GUID().toString();
                    dbRow.put("objectid", databaseId);
                    dbRow.put("name", "mGAP Release: " + getInputValue("version"));
                    dbRow.put("description", null);
                    dbRow.put("libraryId", getLibraryId());
                    dbRow.put("temporary", false);
                    dbRow.put("primarydb", false);
                    dbRow.put("createOwnIndex", false);
                    dbRow.put("container", getContainerUser().getContainer().getId());
                    dbRow.put("created", new Date());
                    dbRow.put("createdby", getContainerUser().getUser().getUserId());
                    dbRow.put("modified", new Date());
                    dbRow.put("modifiedby", getContainerUser().getUser().getUserId());

                    databases.getUpdateService().insertRows(getContainerUser().getUser(), getContainerUser().getContainer(), Arrays.asList(dbRow), new BatchValidationException(), null, new HashMap<>());

                    //then database member
                    TableInfo databaseMembers = getJbrowseUserSchema().getTable("database_members");
                    CaseInsensitiveHashMap<Object> row = new CaseInsensitiveHashMap<>();
                    row.put("database", databaseId);
                    row.put("jsonfile", jsonFileId);
                    row.put("category", "Variants");
                    row.put("container", getContainerUser().getContainer().getId());
                    row.put("created", new Date());
                    row.put("createdby", getContainerUser().getUser().getUserId());
                    row.put("modified", new Date());
                    row.put("modifiedby", getContainerUser().getUser().getUserId());

                    databaseMembers.getUpdateService().insertRows(getContainerUser().getUser(), getContainerUser().getContainer(), Arrays.asList(row), new BatchValidationException(), null, new HashMap<>());

                    _log.info("recreating jbrowse session: " + databaseId);
                    JobRunner jr = JobRunner.getDefault();
                    jr.execute(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            try
                            {
                                JBrowseService.get().reprocessDatabase(getContainerUser().getContainer(), getContainerUser().getUser(), databaseId);
                            }
                            catch (PipelineValidationException e)
                            {
                                _log.error(e.getMessage(), e);
                            }
                        }
                    });

                    return databaseId;
                }
                catch (Exception e)
                {
                    _log.error("Error creating jsonfile: " + String.valueOf(inputValue), e);
                }
            }
        }

        return null;
    }

    private TableInfo getJsonFiles()
    {
        if (_jsonFiles == null)
        {
            _jsonFiles = DbSchema.get("jbrowse", DbSchemaType.Module).getTable("jsonfiles");
        }

        return _jsonFiles;
    }

    private TableInfo getDatabaseMembers()
    {
        if (_databaseMembers == null)
        {
            _databaseMembers = DbSchema.get("jbrowse", DbSchemaType.Module).getTable("database_members");
        }

        return _databaseMembers;
    }

    private UserSchema getJbrowseUserSchema()
    {
        if (_jbus == null)
        {
            _jbus = QueryService.get().getUserSchema(getContainerUser().getUser(), getContainerUser().getContainer(), "jbrowse");
        }

        return _jbus;
    }

    private TableInfo getDatabases()
    {
        if (_databases == null)
        {
            _databases = DbSchema.get("jbrowse", DbSchemaType.Module).getTable("databases");
        }

        return _databases;

    }
}
