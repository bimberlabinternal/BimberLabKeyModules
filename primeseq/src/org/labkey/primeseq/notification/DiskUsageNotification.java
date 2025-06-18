package org.labkey.primeseq.notification;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.ldk.notification.Notification;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.settings.LookAndFeelProperties;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * User: bbimber
 * Date: 8/4/12
 */
public class DiskUsageNotification implements Notification
{
    protected final static Logger _log = LogManager.getLogger(DiskUsageNotification.class);

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
        return "0 0 8 * * 1";
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

            msg.append("<b>Year-to-Date Cluster Usage:</b><p>");
            msg.append("<table border=1 style='border-collapse: collapse;'><tr style='font-weight: bold;'><td>Account</td><td>NormShares</td><td>RawUsage</td><td>EffectiveUsage</td><td>FairShare</td></tr>");

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
                long shares = StringUtils.isEmpty(els[4]) ? 0 : Long.parseLong(els[4]);
                msg.append("<td>").append(String.format("%,d", shares)).append("</td>");
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

        List<Map<String, Object>> byMonth = getClusterUsageByMonth(Arrays.asList("bimberlab", "onprcgenetics"), 12);
        byMonth.sort(Comparator.comparing(o -> String.valueOf(o.get("Account"))));

        msg.append("<b>Cluster Usage By Month:</b><p>");
        msg.append("<table border=1 style='border-collapse: collapse;'><tr style='font-weight: bold;'><td>Account</td><td>Month</td><td>CPU</td><td>GPU</td><td>Compute Units</td></tr>");
        byMonth.forEach(map -> {
            long cpu = (Long)map.get("CPU");
            long gpu = (Long)map.get("GPU");
            double units = (double)(cpu/6000) + (gpu/600);
            Date start = (Date)map.get("Start");

            msg.append("<tr><td>").append(map.get("Account")).append("</td><td>").append(getDateTimeFormat(c).format(start)).append("</td><td>").append(String.format("%,d", cpu)).append("</td><td>").append(String.format("%,d", gpu)).append("</td><td>").append(String.format("%,d", units)).append("</td></tr>");
        });

        msg.append("</table>");
        msg.append("<br><br>\n");
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

        msg.append("<br><br>\n");
    }

    private List<Map<String, Object>> getClusterUsageByMonth(List<String> accounts, int numMonths)
    {
        Calendar currentCal = Calendar.getInstance();
        int currentMonth = currentCal.get(Calendar.MONTH);
        int currentYear = currentCal.get(Calendar.YEAR);

        List<Map<String, Object>> results = new ArrayList<>();

        int offset = 0;
        while (offset < numMonths)
        {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.YEAR, currentYear);
            cal.set(Calendar.MONTH, currentMonth - offset);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            Date start = cal.getTime();

            Calendar endCal = Calendar.getInstance();
            endCal.setTime(start);
            endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH));

            // Set to last second of the day:
            endCal.add(Calendar.DATE, 1);
            endCal.add(Calendar.MILLISECOND, -1);
            Date end = endCal.getTime();

            results.addAll(getClusterUsageForInterval(accounts, start, end));

            offset++;
        }

        return results;
    }

    private @NotNull List<Map<String, Object>> getClusterUsageForInterval(List<String> accounts, Date start, Date end)
    {
        if (!SystemUtils.IS_OS_LINUX)
        {
            return Collections.emptyList();
        }

        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

        try
        {
            List<String> args = new ArrayList<>(Arrays.asList("ssh", "-q", "labkey_submit@arc", "/usr/local/bin/sreport-accts-summary", "Accounts=" + StringUtils.join(accounts, ",")));
            if (start != null)
            {
                args.add("Start=" + dateFormat.format(start));
            }

            if (end != null)
            {
                args.add("End=" + dateFormat.format(end));
            }

            SimpleScriptWrapper wrapper = new SimpleScriptWrapper(_log);
            String results = wrapper.executeWithOutput(args);

            AtomicBoolean foundHeader = new AtomicBoolean(false);
            List<Map<String, Object>> ret = Arrays.stream(results.split("\n")).map(x -> {
                if (x.startsWith("Account|"))
                {
                    foundHeader.set(true);
                    return null;
                }
                else if (!foundHeader.get())
                {
                    return null;
                }

                String[] els = x.split("\\|");

                if (els.length != 3)
                {
                    _log.error("Unexpected line: " + StringUtils.join(els, "<>"));
                    return null;
                }

                Map<String, Object> map = new HashMap<>(Map.of("Account", els[0], "CPU", Long.parseLong(els[1]), "GPU", Long.parseLong(els[2])));
                return map;
            }).filter(Objects::nonNull).toList();

            ret.forEach(map -> {
                map.put("Start", start);
                map.put("End", end);
            });

            return ret;
        }
        catch (PipelineJobException e)
        {
            _log.error("Error fetching slurm summary", e);
            return Collections.emptyList();
        }
    }
}