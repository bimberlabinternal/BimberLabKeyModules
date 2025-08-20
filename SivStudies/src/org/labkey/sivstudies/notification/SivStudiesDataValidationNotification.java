package org.labkey.sivstudies.notification;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.ldk.notification.AbstractNotification;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.sivstudies.SivStudiesModule;

import java.util.Date;

public class SivStudiesDataValidationNotification extends AbstractNotification
{
    public SivStudiesDataValidationNotification()
    {
        super(ModuleLoader.getInstance().getModule(SivStudiesModule.class));
    }

    @Override
    public String getName()
    {
        return "SIV Studies Validation";
    }

    @Override
    public String getCategory()
    {
        return "Studies";
    }

    @Override
    public String getCronString()
    {
        return "0 0 6 * * ?";
    }

    @Override
    public String getScheduleDescription()
    {
        return "every day at 6:00AM";
    }

    @Override
    public String getDescription()
    {
        return "SIV Study Data Validation Alerts";
    }

    @Override
    public String getEmailSubject(Container c)
    {
        return "SIV Study Data Validation: " + c.getName();
    }

    @Override
    public @Nullable String getMessageBodyHTML(Container c, User u)
    {
        StringBuilder msg = new StringBuilder();
        Date now = new Date();

        duplicateInfectionCheck(c, u, msg);

        if (!msg.isEmpty())
        {
            msg.insert(0, "This email contains a series of automatic alerts about the SIV study data.  It was run on: " + getDateFormat(c).format(now) + " at " + AbstractNotification._timeFormat.format(now) + ".<p>");
        }

        return msg.toString();
    }

    private TableInfo getTableInfo(User u, Container c, String schemaName, String queryName)
    {
        UserSchema us = QueryService.get().getUserSchema(u, c, schemaName);
        if (us == null)
        {
            throw new IllegalStateException("Missing user schema: " + schemaName);
        }

        TableInfo ti = us.getTable(queryName);
        if (ti == null)
        {
            throw new IllegalStateException("Missing table: " + schemaName + "." + queryName);
        }

        return ti;
    }

    private void duplicateInfectionCheck(Container c, User u, StringBuilder msg)
    {
        String schemaName = "study";
        String queryName = "duplicateInfectionDates";

        TableInfo ti = getTableInfo(u, c, schemaName, queryName);

        TableSelector ts = new TableSelector(ti);
        long count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("<b>WARNING: There are ").append(count).append(" duplicate infection date records</b><br>\n");
            msg.append("<p><a href='").append(getExecuteQueryUrl(c, schemaName, queryName, null)).append("'>Click here to view them</a><br>\n\n");
            msg.append("<hr>\n\n");
        }
    }

    private void infectionAnchorDateDiscordance(Container c, User u, StringBuilder msg)
    {
        String schemaName = "study";
        String queryName = "infectionAnchorDateDiscordance";

        TableInfo ti = getTableInfo(u, c, schemaName, queryName);

        TableSelector ts = new TableSelector(ti);
        long count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("<b>WARNING: There are ").append(count).append(" records with discordant treatment and anchor date SIV infection records</b><br>\n");
            msg.append("<p><a href='").append(getExecuteQueryUrl(c, schemaName, queryName, null)).append("'>Click here to view them</a><br>\n\n");
            msg.append("<hr>\n\n");
        }
    }
}
