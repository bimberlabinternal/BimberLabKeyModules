package org.labkey.mcc.query;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbSequence;
import org.labkey.api.data.DbSequenceManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.MailHelper;
import org.labkey.mcc.MccManager;
import org.labkey.mcc.MccSchema;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.internet.InternetAddress;
import java.util.HashSet;
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

    public void sendNotification(int rowid) {
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
}
