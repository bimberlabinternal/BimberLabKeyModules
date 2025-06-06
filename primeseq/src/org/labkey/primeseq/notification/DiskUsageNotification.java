package org.labkey.primeseq.notification;

import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.ldk.notification.Notification;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.settings.LookAndFeelProperties;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * User: bbimber
 * Date: 8/4/12
 */
public class DiskUsageNotification implements Notification
{
    protected final static Logger _log = LogManager.getLogger(DiskUsageNotification.class);
    private static final String lastSave = "lastSave";
    private NumberFormat _pctFormat = null;

    private static final String PROP_CATEGORY = "primeseq.DiskUsageNotification";

    @Override
    public String getName()
    {
        return "Disk/Cluster Usage Notification";
    }

    @Override
    public String getCategory()
    {
        return "Admin";
    }

    @Override
    public boolean isAvailable(Container c)
    {
        return true;
    }

    @Override
    public String getDescription()
    {
        return "This runs once a week to summarize file system and cluster usage";
    }

    @Override
    public String getEmailSubject(Container c)
    {
        return "Disk/Cluster Usage: " + getDateTimeFormat(c).format(new Date());
    }

    public DateFormat getDateTimeFormat(Container c)
    {
        return new SimpleDateFormat(LookAndFeelProperties.getInstance(c).getDefaultDateTimeFormat());
    }

    @Override
    public String getCronString()
    {
        return "0 8 * * 1 ?";
    }

    @Override
    public String getScheduleDescription()
    {
        return "Every Monday at 8AM";
    }

    private Map<String, String> getSavedValues(Container c)
    {
        return PropertyManager.getProperties(c, PROP_CATEGORY);
    }

    @Override
    public String getMessageBodyHTML(Container c, User u)
    {
        Date start = new Date();

        _pctFormat = NumberFormat.getPercentInstance();
        _pctFormat.setMaximumFractionDigits(1);

        Map<String, String> saved = getSavedValues(c);
        Map<String, String> newValues = new HashMap<>();

        StringBuilder msg = new StringBuilder();
        StringBuilder alerts = new StringBuilder();

        getDiskUsageStats(c, u, msg);

        if (alerts.length() > 0)
        {
            alerts.insert(0, "<b>The following alerts were generated:</b><p>");
            alerts.append("<hr>");
            msg.insert(0, alerts);
        }

        msg.insert(0, "This email contains a series of alerts designed for site admins.  It was run on: " + getDateTimeFormat(c).format(new Date()) + ".  Runtime: " + DurationFormatUtils.formatDurationWords((new Date()).getTime() - start.getTime(), true, true) + "<p>");

        return msg.toString();
    }

    private void getDiskUsageStats(Container c, User u, final StringBuilder msg)
    {
        if (!SystemUtils.IS_OS_LINUX)
        {
            return;
        }

        try
        {
            SimpleScriptWrapper wrapper = new SimpleScriptWrapper(_log);

            String results = wrapper.executeWithOutput(Arrays.asList("df", "-h", "/home/groups/BimberLab/", "/home/groups/OnprcColonyData/", "/home/groups/prime-seq/", "/home/exacloud/gscratch/prime-seq/"));

            msg.append("<b>Disk Usage Stats:</b><p>");
            msg.append("<table>");
            Arrays.stream(results.split("\n")).forEach(x -> {
                msg.append("<tr>");
                Arrays.stream(x.split("[ ]+")).forEach(cell -> {
                    msg.append("<td>");
                    msg.append(cell);
                    msg.append("</td>");
                });

                msg.append("</tr>");
            });
            msg.append("</table>");
        }
        catch (PipelineJobException e)
        {
            _log.error("Error running df", e);
        }

        msg.append("<p>\n");
    }
}