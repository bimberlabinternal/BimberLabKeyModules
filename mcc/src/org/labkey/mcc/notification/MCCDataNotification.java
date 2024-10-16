package org.labkey.mcc.notification;

import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.ehr.notification.AbstractEHRNotification;
import org.labkey.api.module.Module;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.mcc.MccManager;

import java.util.Date;

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
    public String getCategory()
    {
        return "MCC";
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
        StringBuilder msg = new StringBuilder();
        Date now = new Date();

        Container mccData = MccManager.get().getMCCContainer(c);
        if (!mccData.hasPermission(u, ReadPermission.class))
        {
            throw new UnauthorizedException("User does not have read permission on folder: " + mccData.getPath());
        }

        doParentSexCheck(mccData, u, msg);
        doU24AssignedCheck(mccData, u , msg);
        doMissingIdCheck(mccData, u, msg);
        doZeroWeightCheck(mccData, u, msg);
        doDuplicationCheck(mccData, u, msg);

        //since we dont want to trigger an email if there's no alerts, conditionally append the title
        if (msg.length() > 0)
        {
            msg.insert(0, "This email contains a series of automatic alerts about the MCC.  It was run on: " + getDateFormat(c).format(now) + " at " + AbstractEHRNotification._timeFormat.format(now) + ".<p>");
        }

        return msg.toString();
    }

    protected void doMissingIdCheck(final Container c, User u, final StringBuilder msg)
    {
        TableInfo ti = getUserSchemaByName(c, u, "mcc").getTable("aggregatedDemographics");

        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("Id"), null, CompareType.ISBLANK);
        TableSelector ts = new TableSelector(ti, filter, null);
        long count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("<b>WARNING: There are ").append(count).append(" animals missing MCC IDs\n");
            msg.append("<p><a href='").append(getExecuteQueryUrl(c, "mcc", "aggregatedDemographics", null)).append("&").append(filter.toQueryString("query")).append("'>Click here to view them</a><br>\n\n");
            msg.append("<hr>\n\n");
        }
    }

    protected void doZeroWeightCheck(final Container c, User u, final StringBuilder msg)
    {
        TableInfo ti = getUserSchemaByName(c, u, "mcc").getTable("aggregatedDemographics");

        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("mostRecentWeight"), 0, CompareType.EQUAL);
        TableSelector ts = new TableSelector(ti, filter, null);
        long count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("<b>WARNING: There are ").append(count).append(" animals listing weight as zero\n");
            msg.append("<p><a href='").append(getExecuteQueryUrl(c, "mcc", "aggregatedDemographics", null)).append("&").append(filter.toQueryString("query")).append("'>Click here to view them</a><br>\n\n");
            msg.append("<hr>\n\n");
        }
    }

    protected void doU24AssignedCheck(final Container c, User u, final StringBuilder msg)
    {
        TableInfo demographics = getStudySchema(c, u).getTable("demographics");

        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("availability"), "available for transfer", CompareType.EQUAL);
        filter.addCondition(FieldKey.fromString("calculated_status"), "Alive", CompareType.NEQ_OR_NULL);

        TableSelector ts = new TableSelector(demographics, filter, null);
        long count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("<b>WARNING: There are ").append(count).append(" animals marked 'available for transfer' that are not alive\n");
            msg.append("<p><a href='").append(getExecuteQueryUrl(c, "study", "demographics", null)).append("&").append(filter.toQueryString("query")).append("'>Click here to view them</a><br>\n\n");
            msg.append("<hr>\n\n");
        }

        filter = new SimpleFilter(FieldKey.fromString("u24_status"), true, CompareType.EQUAL);
        filter.addCondition(FieldKey.fromString("calculated_status"), "Alive", CompareType.NEQ_OR_NULL);

        ts = new TableSelector(demographics, filter, null);
        count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("<b>WARNING: There are ").append(count).append(" U24-assigned animals that are not alive\n");
            msg.append("<p><a href='").append(getExecuteQueryUrl(c, "study", "demographics", null)).append("&").append(filter.toQueryString("query")).append("'>Click here to view them</a><br>\n\n");
            msg.append("<hr>\n\n");
        }
    }

    protected void doParentSexCheck(final Container c, User u, final StringBuilder msg)
    {
        TableInfo demographics = getStudySchema(c, u).getTable("demographics");

        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("dam/Demographics/gender/origgender"), "f", CompareType.NEQ);
        TableSelector ts = new TableSelector(demographics, filter, null);
        long count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("<b>WARNING: There are ").append(count).append(" dams with gender not equal to f</b><br>\n");
            msg.append("<p><a href='").append(getExecuteQueryUrl(c, "study", "demographics", null)).append("&query.viewName=With Parent Gender&").append(filter.toQueryString("query")).append("'>Click here to view them</a><br>\n\n");
            msg.append("<hr>\n\n");
        }

        filter = new SimpleFilter(FieldKey.fromString("sire/Demographics/gender/origgender"), "m", CompareType.NEQ);
        ts = new TableSelector(demographics, filter, null);
        count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("<b>WARNING: There are ").append(count).append(" sires with gender not equal to m</b><br>\n");
            msg.append("<p><a href='").append(getExecuteQueryUrl(c, "study", "demographics", null)).append("&query.viewName=With Parent Gender&").append(filter.toQueryString("query")).append("'>Click here to view them</a><br>\n\n");
            msg.append("<hr>\n\n");
        }
    }

    protected void doDuplicationCheck(final Container c, User u, final StringBuilder msg)
    {
        TableInfo ti = getUserSchemaByName(c, u, "mcc").getTable("duplicateDemographics");
        TableSelector ts = new TableSelector(ti);
        long count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("<b>WARNING: There are ").append(count).append(" demographics records with duplicated MCC IDs\n");
            msg.append("<p><a href='").append(getExecuteQueryUrl(c, "mcc", "duplicateDemographics", null)).append("'>Click here to view them</a><br>\n\n");
            msg.append("<hr>\n\n");
        }

        ti = getUserSchemaByName(c, u, "mcc").getTable("duplicatedAggregatedDemographics");
        ts = new TableSelector(ti);
        count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("<b>WARNING: There are ").append(count).append(" aggregated demographics records with duplicated MCC IDs\n");
            msg.append("<p><a href='").append(getExecuteQueryUrl(c, "mcc", "duplicatedAggregatedDemographics", null)).append("'>Click here to view them</a><br>\n\n");
            msg.append("<hr>\n\n");
        }

        ti = getUserSchemaByName(c, u, "mcc").getTable("duplicatedAggregatedDemographicsParents");
        ts = new TableSelector(ti);
        count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("<b>WARNING: There are ").append(count).append(" aggregated demographics parent records with duplicated MCC IDs\n");
            msg.append("<p><a href='").append(getExecuteQueryUrl(c, "mcc", "duplicatedAggregatedDemographicsParents", null)).append("'>Click here to view them</a><br>\n\n");
            msg.append("<hr>\n\n");
        }
    }
}
