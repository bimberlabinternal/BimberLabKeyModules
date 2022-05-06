package org.labkey.mcc.query;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbSequence;
import org.labkey.api.data.DbSequenceManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.mcc.MccManager;
import org.labkey.mcc.MccSchema;

import javax.mail.Address;
import javax.mail.Message;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by bimber
 */
public class TriggerHelper
{
    private Container _container = null;
    private User _user = null;
    private static final Logger _log = LogManager.getLogger(TriggerHelper.class);
    private static final String SEQUENCE_NAME = "org.labkey.mcc.MCC_ALIAS";

    private TableInfo _animalMapping = null;

    public TriggerHelper(int userId, String containerId)
    {
        _user = UserManager.getUser(userId);
        if (_user == null)
            throw new RuntimeException("User does not exist: " + userId);

        _container = ContainerManager.getForId(containerId);
        if (_container == null)
            throw new RuntimeException("Container does not exist: " + containerId);

    }

    public String getNextAlias()
    {
        DbSequence sequence = DbSequenceManager.get((ContainerManager.getRoot()), SEQUENCE_NAME);

        return "MCC" + StringUtils.leftPad(String.valueOf(sequence.next()), 5, "0");
    }


    public boolean isAliasInUse(String alias)
    {
        if (_animalMapping == null)
        {
            // Perform this query site-wide:
            _animalMapping = DbSchema.get(MccSchema.NAME, DbSchemaType.Module).getTable(MccSchema.TABLE_ANIMAL_MAPPING);
        }

        return new TableSelector(_animalMapping, new SimpleFilter(FieldKey.fromString("externalAlias"), alias), null).exists();
    }

    public void ensureReviewRecordsCreated(String objectId, String status, @Nullable String previousStatus, int score)
    {
        if (objectId == null)
        {
            _log.error("No objectId provided, cannot update animalRequest record");
            return;
        }

        try
        {
            MccManager.RequestStatus st = MccManager.RequestStatus.resolveStatus(status);
            if (st == MccManager.RequestStatus.Draft)
            {
                return;
            }

            // NOTE: regular users (opposed to those with MccRequestAdminPermission) cannot read this table. Therefore do these operations using the schema layer, not QUS
            TableInfo scoreTable = MccSchema.getInstance().getSchema().getTable(MccSchema.TABLE_REQUEST_SCORE);
            TableSelector ts = new TableSelector(scoreTable, PageFlowUtil.set("rowid", "preliminaryScore"), new SimpleFilter(FieldKey.fromString("requestid"), objectId), null);
            if (!ts.exists())
            {
                Map<String, Object> toInsert = new CaseInsensitiveHashMap<>();
                toInsert.put("requestId", objectId);
                toInsert.put("preliminaryScore", score);

                toInsert.put("container", _container);
                toInsert.put("created", new Date());
                toInsert.put("createdby", _user.getUserId());
                toInsert.put("modified", new Date());
                toInsert.put("modifiedby", _user.getUserId());

                Table.insert(_user, scoreTable, toInsert);
            }
            else
            {
                // Ensure score is accurate in the case the underlying data changed:
                List<Map> records = ts.getArrayList(Map.class);
                if (records.size() != 1)
                {
                    _log.error("More than one requestScore record found for requestId: " + objectId);
                }

                Map<String, Object> row = records.get(0);
                if (score != (Integer)row.get("preliminaryScore"))
                {
                    row.put("preliminaryScore", score);
                    row.put("modified", new Date());
                    row.put("modifiedby", _user.getUserId());
                    Table.update(_user, scoreTable, row, row.get("rowid"));
                }
            }

            // This indicates the form was submitted immediately (i.e. not a draft), or it was in draft state and has advanced.
            MccManager.RequestStatus st2 = previousStatus == null ? null : MccManager.RequestStatus.resolveStatus(previousStatus);
            if (st2 == null || st2 == MccManager.RequestStatus.Draft)
            {
                sendInitialNotification();
            }
        }
        catch (IllegalArgumentException e)
        {
            _log.error("Unknown MCC status: " + status);
        }
        catch (Exception e)
        {
            _log.error("Error in ensureReviewRecordsCreated", e);
        }
    }

    private void sendInitialNotification() {
        Set<Address> emails = MccManager.get().getRequestNotificationUserEmails();
        if (emails == null || emails.isEmpty())
        {
            _log.error("An MCC request was finalized but there are no notification users");
            return;
        }

        try
        {
            MailHelper.MultipartMessage mail = MailHelper.createMultipartMessage();
            mail.setFrom("mcc-do-not-reply@ohsu.edu");
            mail.setSubject("MCC Animal Request");

            Container rc = MccManager.get().getMCCRequestContainer();
            DetailsURL url = DetailsURL.fromString("/mcc/mccRequestAdmin.view", rc);
            mail.setEncodedHtmlContent("An animal request was submitted on MCC.  <a href=\"" + AppProps.getInstance().getBaseServerUrl() + url.getActionURL().toString()+ "\">Click here to view/approve this request</a>");
            mail.addRecipients(Message.RecipientType.TO, emails.toArray(new Address[0]));

            MailHelper.send(mail, _user, _container);
        }
        catch (Exception e)
        {
            _log.error("Unable to send MCC email", e);
        }
    }

    public void cascadeDelete(String schemaName, String queryName, String keyField, Object keyValue) throws SQLException
    {
        UserSchema schema = QueryService.get().getUserSchema(_user, _container, schemaName);
        if (schema == null)
            throw new RuntimeException("Unknown schema: " + schemaName);

        TableInfo table = schema.getTable(queryName);
        if (table == null)
            throw new RuntimeException("Unknown table: " + schemaName + "." + queryName);

        if (!_container.hasPermission(_user, DeletePermission.class))
            throw new UnauthorizedException("User does not have permission to delete from the table: " + table.getPublicName());

        try
        {
            List<Map<String, Object>> toDelete = new TableSelector(table, PageFlowUtil.set("rowid"), new SimpleFilter(FieldKey.fromString(keyField), keyValue), null).stream(Integer.class).map(x -> new CaseInsensitiveHashMap<Object>(Collections.singletonMap("rowId", x))).collect(Collectors.toList());
            table.getUpdateService().deleteRows(_user, _container, toDelete, null, null);
        }
        catch (QueryUpdateServiceException | InvalidKeyException | BatchValidationException e)
        {
            _log.error("Unable to delete from: " + queryName, e);
        }
    }

    public boolean hasPermission(String status)
    {
        try
        {
            return MccManager.RequestStatus.resolveStatus(status).canEdit(_user, _container);
        }
        catch (IllegalArgumentException e)
        {
            _log.error("Unknown MCC status: " + status);
            return false;
        }
    }

    public String resolveObjectId(int rowid)
    {
        return new TableSelector(MccSchema.getInstance().getSchema().getTable(MccSchema.TABLE_ANIMAL_REQUESTS), PageFlowUtil.set("objectid"), new SimpleFilter(FieldKey.fromString("rowid"), rowid), null).getObject(String.class);
    }
}
