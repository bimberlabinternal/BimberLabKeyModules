package org.labkey.mgap.columnTransforms;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Results;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.jbrowse.JBrowseService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.mgap.mGAPSchema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by bimber on 5/15/2017.
 */
public class JBrowseSessionTransform extends AbstractVariantTransform
{
    private transient TableInfo _jsonFiles;
    private transient TableInfo _databaseMembers;
    private transient TableInfo _databases;
    private transient UserSchema _jbus;

    protected String getDataFileUrlField()
    {
        return "vcfId/dataid/DataFileUrl";
    }

    @Override
    protected Object doTransform(Object inputValue)
    {
        String releaseId = (String)getInputValue("objectId");
        if (releaseId == null)
        {
            getStatusLogger().error("no release ID for variantRelease row");
        }

        Object input = getInputValue(getDataFileUrlField());
        if (input == null)
        {
            throw new IllegalArgumentException("DataFileUrl was null for key: " + getDataFileUrlField());
        }

        Integer outputFileId = getOrCreateOutputFile(input, getInputValue("objectId"), null);
        if (outputFileId != null)
        {
            //find database ID, if exists, based on name:
            UserSchema us = getJbrowseUserSchema();
            TableSelector ts = new TableSelector(us.getTable("databases"), PageFlowUtil.set("objectid"), new SimpleFilter(FieldKey.fromString("name"), getDatabaseName()), new Sort("-rowid"));
            if (ts.exists())
            {
                String databaseId = ts.getArrayList(String.class).stream().toList().get(0);
                getStatusLogger().info("jbrowse database exists using name: " + getDatabaseName());

                boolean hadChanges = addTracks(databaseId, releaseId);
                if (hadChanges)
                {
                    recreateSession(databaseId);
                }

                return databaseId;
            }

            String databaseId = new GUID().toString();
            try
            {
                getStatusLogger().info("creating jbrowse database: " + databaseId + ", for output file: " + outputFileId);

                //create database
                TableInfo databases = getJbrowseUserSchema().getTable("databases");
                CaseInsensitiveHashMap<Object> dbRow = new CaseInsensitiveHashMap<>();
                dbRow.put("objectid", databaseId);
                dbRow.put("name", getDatabaseName());
                dbRow.put("description", null);
                dbRow.put("libraryId", getLibraryId());
                dbRow.put("temporary", false);
                dbRow.put("container", getContainerUser().getContainer().getId());
                dbRow.put("created", new Date());
                dbRow.put("createdby", getContainerUser().getUser().getUserId());
                dbRow.put("modified", new Date());
                dbRow.put("modifiedby", getContainerUser().getUser().getUserId());
                dbRow.put("jsonConfig", getSessionJson());

                databases.getUpdateService().insertRows(getContainerUser().getUser(), getContainerUser().getContainer(), List.of(dbRow), new BatchValidationException(), null, new HashMap<>());
            }
            catch (Exception e)
            {
                getStatusLogger().error("Error creating database: " + inputValue, e);
            }

            addTracks(databaseId, releaseId);
            recreateSession(databaseId);

            return databaseId;
        }
        else
        {
            getStatusLogger().info("output file was null for incoming release: " + releaseId);
        }

        return null;
    }

    protected String getSessionJson()
    {
        return "{\"trackSelector\": {\"sortHierarchical\": false},\"trackHeight\": 400,\"defaultLocation\":\"4:137934540..137937146\"}";
    }

    private void recreateSession(final String databaseId)
    {
        // Note: because this transction hasnt committed yet, the DB record will not exist yet, unless it was created in the previous ETL iteration
        getStatusLogger().info("recreating jbrowse session: " + databaseId);
        DbScope.getLabKeyScope().addCommitTask(() -> {
            try
            {
                JBrowseService.get().reprocessDatabase(getContainerUser().getUser(), databaseId);
            }
            catch (PipelineValidationException e)
            {
                getStatusLogger().error(e.getMessage(), e);
            }
        }, DbScope.CommitTaskOption.POSTCOMMIT);
    }

    protected boolean addTracks(final String databaseId, String releaseId)
    {
        //then JSONfiles/database members
        List<FieldKey> fks = Arrays.asList(
                FieldKey.fromString("trackName"),
                FieldKey.fromString("label"),
                FieldKey.fromString("category"),
                FieldKey.fromString("url"),
                FieldKey.fromString("description"),
                FieldKey.fromString("isprimarytrack"),
                FieldKey.fromString("vcfId/dataid/DataFileUrl"),
                FieldKey.fromString("releaseId/luceneIndex"),
                FieldKey.fromString("releaseId/luceneIndex/dataid/DataFileUrl"),
                FieldKey.fromString("vcfIndexId"),
                FieldKey.fromString("vcfIndexId/dataid/DataFileUrl")
        );

        TableInfo tracksPerRelease = QueryService.get().getUserSchema(getContainerUser().getUser(), getContainerUser().getContainer(), mGAPSchema.NAME).getTable(mGAPSchema.TABLE_TRACKS_PER_RELEASE);
        Map<FieldKey, ColumnInfo> colMap = QueryService.get().getColumns(tracksPerRelease, fks);

        TableSelector ts = new TableSelector(tracksPerRelease, colMap.values(), new SimpleFilter(FieldKey.fromString("releaseId"), releaseId), null);
        if (!ts.exists())
        {
            getStatusLogger().error("no track records found for release: " + releaseId);
        }

        final AtomicBoolean hadChanges = new AtomicBoolean(false);
        final Set<String> jsonFiles = new HashSet<>();
        ts.forEachResults(rs -> {
            try
            {
                getStatusLogger().info("possibly creating track for: " + rs.getString(FieldKey.fromString("trackName")));
                String jsonFile = getOrCreateJsonFile(rs, "vcfId/dataid/DataFileUrl");
                jsonFiles.add(jsonFile);
                boolean added = getOrCreateDatabaseMember(databaseId, jsonFile);
                if (added)
                {
                    hadChanges.set(true);
                }
            }
            catch (Exception e)
            {
                getStatusLogger().error(e.getMessage(), e);
            }
        });

        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("database"), databaseId);
        filter.addCondition(FieldKey.fromString("jsonfile"), jsonFiles, CompareType.NOT_IN);
        TableSelector ts2 = new TableSelector(getDatabaseMembers(), PageFlowUtil.set("rowid"), filter, null);
        if (ts2.exists())
        {
            List<Integer> rowIds = ts2.getArrayList(Integer.class);

            getStatusLogger().info("Deleting " + rowIds.size() + " database_member rows for: " + databaseId);
            List<Map<String, Object>> toDelete = new ArrayList<>();
            rowIds.forEach(rowId -> toDelete.add(new CaseInsensitiveHashMap<>(Map.of("rowid", rowId))));
            try
            {
                getJbrowseUserSchema().getTable("database_members").getUpdateService().deleteRows(getContainerUser().getUser(), getContainerUser().getContainer(), toDelete, null, null);
                hadChanges.set(true);
            }
            catch (InvalidKeyException | BatchValidationException | SQLException |QueryUpdateServiceException e)
            {
                getStatusLogger().error(e);
            }
        }

        return hadChanges.get();
    }

    private void ensureLuceneData(String objectId, boolean hasIndex)
    {
        //determine if there is already a JSONfile for this outputfile
        TableSelector ts1 = new TableSelector(getJsonFiles(), PageFlowUtil.set("container"), new SimpleFilter(FieldKey.fromString("objectid"), objectId), null);
        if (!ts1.exists())
        {
            getStatusLogger().error("expected jsonfile to exist: " + objectId);
            return;
        }

        try
        {
            String containerId = ts1.getObject(String.class);

            Map<String, Object> row = new CaseInsensitiveHashMap<>();
            row.put("objectid", objectId);
            row.put("container", containerId);
            row.put("trackJson", getTrackJson(hasIndex));

            TableInfo jsonFiles = getJbrowseUserSchema().getTable("jsonfiles");
            jsonFiles.getUpdateService().updateRows(getContainerUser().getUser(), getContainerUser().getContainer(), Arrays.asList(row), Arrays.asList(new CaseInsensitiveHashMap<>(Map.of("objectid", objectId))), new BatchValidationException(), null, null);
        }
        catch (SQLException | QueryUpdateServiceException | BatchValidationException | InvalidKeyException e)
        {
            getStatusLogger().error("Unable to update lucene config", e);
        }
    }

    protected boolean getOrCreateDatabaseMember(String databaseId, String jsonFileId) throws Exception
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("database"), databaseId);
        filter.addCondition(FieldKey.fromString("jsonfile"), jsonFileId);

        if (new TableSelector(getDatabaseMembers(), filter, null).exists())
        {
            getStatusLogger().info("database member exists for: " + jsonFileId);
            return false;
        }

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

        getStatusLogger().info("creating database member for: " + jsonFileId);
        databaseMembers.getUpdateService().insertRows(getContainerUser().getUser(), getContainerUser().getContainer(), List.of(row), new BatchValidationException(), null, new HashMap<>());

        return true;
    }

    protected TableInfo getJsonFiles()
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

    protected UserSchema getJbrowseUserSchema()
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

    private String getOrCreateJsonFile(Results rs, String fieldKey) throws SQLException
    {
        String value = rs.getString(FieldKey.fromString(fieldKey));
        if (value == null)
        {
            getStatusLogger().info(fieldKey + " is null, skipping in getOrCreateJsonFile()");
            return null;
        }

        Integer outputFileId = getOrCreateOutputFile(value, getInputValue("objectId"), rs.getString("label"));

        boolean isDefaultTrack = rs.getObject(FieldKey.fromString("isprimarytrack")) != null && rs.getBoolean(FieldKey.fromString("isprimarytrack"));

        //determine if there is already a JSONfile for this outputfile
        TableSelector ts1 = new TableSelector(getJsonFiles(), PageFlowUtil.set("objectid"), new SimpleFilter(FieldKey.fromString("outputfile"), outputFileId), null);
        if (ts1.exists())
        {
            getStatusLogger().info("jsonfile already exists for output: " + outputFileId);
            String objectId = ts1.getArrayList(String.class).get(0);
            if (isDefaultTrack)
            {
                boolean expectIndex = rs.getObject(FieldKey.fromString("releaseId/luceneIndex")) != null || rs.getObject(FieldKey.fromString("vcfIndexId")) != null;
                ensureLuceneData(objectId, expectIndex);
            }

            return objectId;
        }

        try
        {
            TableInfo jsonFiles = getJbrowseUserSchema().getTable("jsonfiles");
            CaseInsensitiveHashMap<Object> row = new CaseInsensitiveHashMap<>();
            row.put("objectid", new GUID().toString());
            row.put("outputFile", outputFileId);
            row.put("relPath", "tracks/data-" + outputFileId);
            row.put("container", getContainerUser().getContainer().getId());
            row.put("created", new Date());
            row.put("createdby", getContainerUser().getUser().getUserId());
            row.put("modified", new Date());
            row.put("modifiedby", getContainerUser().getUser().getUserId());

            if (isDefaultTrack)
            {
                boolean expectIndex = rs.getObject(FieldKey.fromString("releaseId/luceneIndex")) != null || rs.getObject(FieldKey.fromString("vcfIndexId")) != null;

                boolean hasLuceneIndex = StringUtils.trimToNull(rs.getString(FieldKey.fromString("releaseId/luceneIndex/dataid/DataFileUrl"))) != null ||
                        StringUtils.trimToNull(rs.getString(FieldKey.fromString("vcfIndexId/dataid/DataFileUrl"))) != null;

                if (expectIndex && !hasLuceneIndex)
                {
                    getStatusLogger().warn("Expected VCF index but did not find one for outputId: " + outputFileId);
                }

                getStatusLogger().info("Creating track JSON for primary track, has lucene index: " + expectIndex + " / " + hasLuceneIndex);
                row.put("trackJson", getTrackJson(expectIndex));
            }
            else
            {
                JSONObject meta = new JSONObject();
                if (rs.getObject(FieldKey.fromString("description")) != null)
                {
                    meta.put("Description", rs.getString(FieldKey.fromString("description")));
                }

                if (rs.getObject(FieldKey.fromString("url")) != null)
                {
                    meta.put("Website", rs.getString(FieldKey.fromString("url")));
                }

                String metaStr = meta.isEmpty() ? "" : ", metadata: " + meta;
                row.put("trackJson", "{\"category\":\"" + rs.getString(FieldKey.fromString("category")) + "\",\"visibleByDefault\": false" + metaStr + "}");
            }

            getStatusLogger().info("creating jsonfile for output: " + outputFileId);
            List<Map<String, Object>> rows = jsonFiles.getUpdateService().insertRows(getContainerUser().getUser(), getContainerUser().getContainer(), List.of(row), new BatchValidationException(), null, new HashMap<>());

            return (String) rows.get(0).get("objectid");
        }
        catch (Exception e)
        {
            getStatusLogger().error("Error creating jsonfile for ID: " + outputFileId, e);
        }

        return null;
    }

    protected String getDatabaseName()
    {
        return "mGAP Release: " + getInputValue("version");
    }

    protected String getTrackJson(boolean hasLuceneIndex)
    {
        String indexString = "";

        if (hasLuceneIndex)
        {
            ArrayList<String> infoFields = new TableSelector(QueryService.get().getUserSchema(getContainerUser().getUser(), getContainerUser().getContainer(), mGAPSchema.NAME).getTable(mGAPSchema.TABLE_VARIANT_ANNOTATIONS), PageFlowUtil.set("infoKey"), new SimpleFilter(FieldKey.fromString("isIndexed"), true), null).getArrayList(String.class);
            indexString = ", \"createFullTextIndex\": true,\"infoFieldsForFullTextSearch\":\"" + (infoFields.isEmpty() ? "null" : StringUtils.join(infoFields, ",")) + "\"";
        }

        return "{\"category\":\"mGAP Variant Catalog\",\"visibleByDefault\": true,\"ensemblId\":\"Macaca_mulatta\",\"additionalFeatureMsg\":\"<h2>**The annotations below are primarily derived from human data sources (not macaque), and must be viewed in that context.</h2>\"" + indexString + "}";
    }
}
