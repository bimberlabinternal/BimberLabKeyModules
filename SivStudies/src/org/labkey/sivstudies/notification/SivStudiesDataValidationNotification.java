package org.labkey.sivstudies.notification;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.ldk.notification.AbstractNotification;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.sivstudies.SivStudiesModule;
import org.labkey.sivstudies.query.DefaultDatasetTrigger;

import java.util.Date;

public class SivStudiesDataValidationNotification extends AbstractNotification
{
    protected static final Logger _log = LogHelper.getLogger(DefaultDatasetTrigger.class, "Messages related to SivStudiesDataValidationNotification");

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
        infectionAnchorDateDiscordance(c, u, msg);
        pvlWithoutInfectionDate(c, u, msg);
        idsMissingFromDemographics(c, u, msg);
        missingArtRecord(c, u, msg);
        missingAnchorDates(c, u, msg);
        duplicatePVLs(c, u, msg);

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
        genericQueryCheck(c, u, msg, "study", "duplicateInfectionDates", "duplicate infection date records");
    }

    private void infectionAnchorDateDiscordance(Container c, User u, StringBuilder msg)
    {
        genericQueryCheck(c, u, msg, "study", "infectionAnchorDateDiscordance", "records with discordant treatment and anchor date SIV infection records");
    }

    private void genericQueryCheck(Container c, User u, StringBuilder msg, String schemaName, String queryName, String message)
    {
        genericQueryCheck(c, u, msg, schemaName, queryName, message, null);
    }

    private void genericQueryCheck(Container c, User u, StringBuilder msg, String schemaName, String queryName, String message, @Nullable SimpleFilter filter)
    {
        TableInfo ti = getTableInfo(u, c, schemaName, queryName);

        TableSelector ts = new TableSelector(ti, filter, null);
        long count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("<b>WARNING: There are ").append(count).append(" " + message + "</b><br>\n");
            msg.append("<p><a href='").append(getExecuteQueryUrl(c, schemaName, queryName, null)).append("'>Click here to view them</a><br>\n\n");
            msg.append("<hr>\n\n");
        }
    }

    private void pvlWithoutInfectionDate(Container c, User u, StringBuilder msg)
    {
        genericQueryCheck(c, u, msg, "study", "pvlWithoutInfectionDate", "animals with PVL data but no record of SIV infection");
    }

    private void idsMissingFromDemographics(Container c, User u, StringBuilder msg)
    {
        Study s = StudyService.get().getStudy(getTargetContainer(c));
        if (s == null)
        {
            return;
        }

        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("DataSet/Demographics/" + s.getSubjectColumnName()), null, CompareType.ISBLANK);
        genericQueryCheck(c, u, msg, "study", s.getSubjectNounSingular(), "IDs with data in the study not present in the demographics table", filter);
    }

    private void missingArtRecord(Container c, User u, StringBuilder msg)
    {
        Study s = StudyService.get().getStudy(getTargetContainer(c));
        if (s == null)
        {
            return;
        }

        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("projects/studyDescription"), "ART", CompareType.CONTAINS);
        filter.addCondition(FieldKey.fromString("projects/studyDescription"), "No ART", CompareType.DOES_NOT_CONTAIN);
        filter.addCondition(FieldKey.fromString("sivART/artInitiationDPI"), null, CompareType.ISBLANK);
        genericQueryCheck(c, u, msg, "study", "demographics", "IDs assigned to an ART study without a record of ART", filter);
    }

    private void missingAnchorDates(Container c, User u, StringBuilder msg)
    {
        Study s = StudyService.get().getStudy(getTargetContainer(c));
        if (s == null)
        {
            return;
        }

        genericQueryCheck(c, u, msg, "study", "missingAnchorDates", "records missing from the anchor dates table");
    }

    private void duplicatePVLs(Container c, User u, StringBuilder msg)
    {
        Study s = StudyService.get().getStudy(getTargetContainer(c));
        if (s == null)
        {
            return;
        }

        genericQueryCheck(c, u, msg, "study", "duplicatePVLs", "duplicate PVL records");
    }

    protected Container getTargetContainer(Container c)
    {
        return c.isWorkbookOrTab() ? c.getParent() : c;
    }
}
