package org.labkey.primeseq.notification;

import org.apache.commons.lang3.StringUtils;
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
import java.util.concurrent.atomic.AtomicBoolean;

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

        StringBuilder msg = new StringBuilder();
        getDiskUsageStats(c, u, msg);
        getClusterUsage(c, u, msg);

        msg.insert(0, "This email summarizes disk and cluster usage.  It was run on: " + getDateTimeFormat(c).format(new Date()) + ".  Runtime: " + DurationFormatUtils.formatDurationWords((new Date()).getTime() - start.getTime(), true, true) + "<p>");

        return msg.toString();
    }

    private void getClusterUsage(Container c, User u, final StringBuilder msg)
    {
        if (!SystemUtils.IS_OS_LINUX)
        {
            return;
        }

        try
        {
            SimpleScriptWrapper wrapper = new SimpleScriptWrapper(_log);
            String results = wrapper.executeWithOutput(Arrays.asList("ssh", "-q", "labkey_submit@arc", "sshare", "-U", "-u", "labkey_submit"));

            msg.append("<b>Cluster Usage:</b><p>");
            msg.append("<table border=1 style='border-collapse: collapse;'><tr style='font-weight: bold;'><td>Account</td><td>RawShares</td><td>NormShares</td><td>RawUsage</td><td>EffectiveUsage</td><td>FairShare</td></tr>");

            AtomicBoolean foundHeader = new AtomicBoolean(false);
            Arrays.stream(results.split("\n")).forEach(x -> {
                if (x.startsWith("------------"))
                {
                    foundHeader.set(true);
                    return;
                }
                else if (!foundHeader.get())
                {
                    return;
                }

                String[] els = x.split("[ ]+");

                if (els.length != 7)
                {
                    _log.error("Unexpected line: " + StringUtils.join(els, "<>"));
                    return;
                }

                msg.append("<tr>");
                msg.append("<td>").append(els[0]).append("</td>");
                msg.append("<td>").append(els[3]).append("</td>");
                msg.append("<td>").append(els[4]).append("</td>");
                msg.append("<td>").append(els[5]).append("</td>");
                msg.append("<td>").append(els[6]).append("</td>");
                msg.append("</tr>");
            });
            msg.append("</table>");
        }
        catch (PipelineJobException e)
        {
            _log.error("Error fetching slurm summary", e);
        }

        msg.append("<p>\n");

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
            msg.append("<table border=1 style='border-collapse: collapse;'><tr style='font-weight: bold;'><td>Filesystem</td><td>Size</td><td>Used</td><td>Available</td><td>Percent</td></tr>");
            Arrays.stream(results.split("\n")).forEach(x -> {
                String[] els = x.split("[ ]+");
                if ("Filesystem".equalsIgnoreCase(els[0]))
                {
                    return;
                }

                msg.append("<tr>");
                msg.append("<td>").append(els[0]).append("</td>");
                msg.append("<td>").append(els[1]).append("</td>");
                msg.append("<td>").append(els[2]).append("</td>");
                msg.append("<td>").append(els[3]).append("</td>");
                msg.append("<td>").append(els[4]).append("</td>");
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