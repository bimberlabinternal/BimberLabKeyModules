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
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.MailHelper;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.mcc.MccManager;
import org.labkey.mcc.MccSchema;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.internet.InternetAddress;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        try
        {
            MccManager.RequestStatus st = MccManager.RequestStatus.valueOf(status);
            if (st == MccManager.RequestStatus.Draft)
            {
                return;
            }

            TableSelector ts = new TableSelector(MccSchema.getInstance().getSchema().getTable(MccSchema.TABLE_REQUEST_SCORE), new SimpleFilter(FieldKey.fromString("requestid"), objectId), null);
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

                try
                {
                    BatchValidationException bve = new BatchValidationException();
                    QueryService.get().getUserSchema(_user, _container, MccSchema.NAME).getTable(MccSchema.TABLE_REQUEST_SCORE).getUpdateService().insertRows(_user, _container, Arrays.asList(toInsert), bve, null, null);
                }
                catch (QueryUpdateServiceException | BatchValidationException | DuplicateKeyException | SQLException e)
                {
                    _log.error("Unable to create requestScore rows for request: " + objectId, e);
                }
            }
            else
            {
                // ensure score is accurate in the case of an update:
                List<Map> records = ts.getArrayList(Map.class);

            }

            // This indicates the form was submitted immediately (i.e. not a draft), or it was in draft state and has advanced.
            MccManager.RequestStatus st2 = previousStatus == null ? null : MccManager.RequestStatus.valueOf(previousStatus);
            if (st2 == null || st2 == MccManager.RequestStatus.Draft)
            {
                sendInitialNotification();
            }
        }
        catch (IllegalArgumentException e)
        {
            _log.error("Unknown MCC status: " + status);
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
        // TODO: consider using QUS?
        DbSchema schema = QueryService.get().getUserSchema(_user, _container, schemaName).getDbSchema();
        if (schema == null)
            throw new RuntimeException("Unknown schema: " + schemaName);

        TableInfo table = schema.getTable(queryName);
        if (table == null)
            throw new RuntimeException("Unknown table: " + schemaName + "." + queryName);

        if (!_container.hasPermission(_user, DeletePermission.class))
            throw new UnauthorizedException("User does not have permission to delete from the table: " + table.getPublicName());

        SimpleFilter filter = new SimpleFilter(FieldKey.fromString(keyField), keyValue);
        Table.delete(table, filter);
    }

    public boolean hasPermission(String status)
    {
        try
        {
            return MccManager.RequestStatus.valueOf(status).canEdit(_user, _container);
        }
        catch (IllegalArgumentException e)
        {
            _log.error("Unknown MCC status: " + status);
            return false;
        }
    }
}
