package org.labkey.mgap.etl;

import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.di.TaskRefTask;
import org.labkey.api.jbrowse.JBrowseService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.ContainerUser;
import org.labkey.mgap.mGAPSchema;

import java.util.List;
import java.util.Map;

public class PerformQueuedEtlWorkTask implements TaskRefTask
{
    private ContainerUser _containerUser = null;

    @Override
    public RecordedActionSet run(@NotNull PipelineJob job) throws PipelineJobException
    {
        EtlQueueManager.get().performQueuedWork(_containerUser.getContainer(), job.getLogger());

        // clear/warm caches:
        final TableInfo dbm = QueryService.get().getUserSchema(_containerUser.getUser(), _containerUser.getContainer(), "jbrowse").getTable("database_members");
        new TableSelector(QueryService.get().getUserSchema(_containerUser.getUser(), _containerUser.getContainer(), mGAPSchema.NAME).getTable(mGAPSchema.TABLE_VARIANT_CATALOG_RELEASES), PageFlowUtil.set("objectId", "jbrowseId")).forEachResults(rs -> {
            String jbrowseId = rs.getString(FieldKey.fromString("jbrowseId"));
            List<String> trackIds = new TableSelector(dbm, PageFlowUtil.set("objectid"), new SimpleFilter(FieldKey.fromString("database"), jbrowseId).addCondition(FieldKey.fromString("jsonfile/outputfile/name"), "mGAP Release"), null).getArrayList(String.class);
            if (trackIds.isEmpty())
            {
                job.getLogger().error("No mGAP Release track found for session: " + jbrowseId);
            }
            else if (trackIds.size() > 1)
            {
                job.getLogger().error("More than one mGAP Release track found for session: " + jbrowseId);
            }
            else
            {
                JBrowseService.get().cacheDefaultQuery(_containerUser.getUser(), rs.getString(FieldKey.fromString("objectId")), trackIds.get(0));
            }
        });

        return new RecordedActionSet();
    }

    @Override
    public List<String> getRequiredSettings()
    {
        return null;
    }

    @Override
    public void setSettings(Map<String, String> settings) throws XmlException
    {

    }

    @Override
    public void setContainerUser(ContainerUser containerUser)
    {
        _containerUser = containerUser;
    }
}
