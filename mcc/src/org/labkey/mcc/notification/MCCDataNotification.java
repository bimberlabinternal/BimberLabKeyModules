package org.labkey.mcc.notification;

import org.labkey.api.data.Container;
import org.labkey.api.ehr.notification.AbstractEHRNotification;
import org.labkey.api.module.Module;
import org.labkey.api.security.User;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * User: bimber
 * Date: 4/5/13
 * Time: 2:25 PM
 */
public class MCCDataNotification extends AbstractEHRNotification
{
    public MCCDataNotification(Module owner)
    {
        super(owner);
    }

    @Override
    public String getName()
    {
        return "MCC Data Alerts";
    }

    @Override
    public String getEmailSubject(Container c)
    {
        return "MCC Data Alerts: " + getDateTimeFormat(c).format(new Date());
    }

    @Override
    public String getCronString()
    {
        return "0 0 4 * * ?";
    }

    @Override
    public String getScheduleDescription()
    {
        return "every day at 4:00AM";
    }

    @Override
    public String getDescription()
    {
        return "The report is designed to provide a daily summary of issues related to MCC data integrity";
    }

    @Override
    public String getMessageBodyHTML(Container c, User u)
    {
        Map<String, String> saved = getSavedValues(c);
        Map<String, String> toSave = new HashMap<String, String>();

        StringBuilder msg = new StringBuilder();

        // TODO:
        // missing IDs
        // U24 assigned and dead?
        // available for transfer and dead?
        // dam/sire wrong sex

        saveValues(c, toSave);

        return msg.toString();
    }
}
