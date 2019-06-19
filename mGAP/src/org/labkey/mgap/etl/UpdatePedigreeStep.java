package org.labkey.mgap.etl;

import org.apache.commons.lang3.StringUtils;
import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.di.TaskRefTask;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.ContainerUser;
import org.labkey.mgap.mGAPSchema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UpdatePedigreeStep implements TaskRefTask
{
    protected final Map<String, String> _settings = new CaseInsensitiveHashMap<>();
    protected ContainerUser _containerUser;

    @Override
    public RecordedActionSet run(@NotNull PipelineJob job) throws PipelineJobException
    {
        UserSchema mgapUserSchema = QueryService.get().getUserSchema(_containerUser.getUser(), _containerUser.getContainer(), mGAPSchema.NAME);
        if (mgapUserSchema == null)
        {
            throw new PipelineJobException("Unable to find source schema: " + mGAPSchema.NAME);
        }

        TableInfo pedigreeOverrideTable = mgapUserSchema.getTable(mGAPSchema.TABLE_PEDIGREE_OVERRIDES);
        if (pedigreeOverrideTable == null)
        {
            throw new PipelineJobException("Unable to find table: " + mGAPSchema.TABLE_PEDIGREE_OVERRIDES);
        }

        UserSchema laboratoryUserSchema = QueryService.get().getUserSchema(_containerUser.getUser(), _containerUser.getContainer(), "laboratory");
        if (laboratoryUserSchema == null)
        {
            throw new PipelineJobException("Unable to find source schema: laboratory");
        }

        TableInfo subjectsTable = laboratoryUserSchema.getTable("subjects");
        if (subjectsTable == null)
        {
            throw new PipelineJobException("Unable to find table: subjects");
        }

        List<Map<String, Object>> toUpdate = new ArrayList<>();
        List<Map<String, Object>> oldKeys = new ArrayList<>();
        new TableSelector(pedigreeOverrideTable).forEachResults(rs -> {
            //find existing record for this ID:
            String id = rs.getString(FieldKey.fromString("subjectId"));
            String relationship = rs.getString(FieldKey.fromString("relationship"));
            String correctedValue = StringUtils.trimToNull(rs.getString("correctedValue"));

            TableSelector ts = new TableSelector(subjectsTable, PageFlowUtil.set("subjectname", "mother", "father"), new SimpleFilter(FieldKey.fromString("subjectname"), id), null);
            if (ts.exists())
            {
                Map<String, Object> existing = ts.getMap(id);

                Map<String, Object> toApply = new CaseInsensitiveHashMap<>();
                toApply.put("subjectname", id);

                String targetField;
                switch (relationship)
                {
                    case "Dam":
                        targetField = "mother";
                        break;
                    case "Sire":
                        targetField = "father";
                        break;
                    case "Self":
                        targetField = "subjectname";
                        break;
                    default:
                        job.getLogger().error("Unknown value for relationship: " + rs.getString(FieldKey.fromString("relationship")));
                        return;
                }

                //santity check:
                String expectedOriginalValue = StringUtils.trimToNull(rs.getString("originalValue"));
                String actualOriginalValue = StringUtils.trimToNull((String)existing.get(targetField));
                if (!((actualOriginalValue == null && expectedOriginalValue == null) || (actualOriginalValue != null && actualOriginalValue.equals(expectedOriginalValue))))
                {
                    job.getLogger().error("Skipping override for Id: " + id + ", because the expected originalValue did not match the existing value for field: " + targetField + ". values were: " + expectedOriginalValue + " / " + actualOriginalValue);
                    return;
                }

                if ("subjectname".equals(targetField) && correctedValue == null)
                {
                    job.getLogger().error("Skipping override for Id: " + id + ", because a null corrected value was provided");
                    return;
                }

                toApply.put(targetField, correctedValue);

                Map<String, Object> oldKey = new CaseInsensitiveHashMap<>();
                oldKey.put("subjectname", id);

                toUpdate.add(toApply);
                oldKeys.add(oldKey);
            }
            else
            {
                job.getLogger().error("Pedigree overrides contains record for " + id + ", but was not found in subjects table");
            }
        });

        if (!toUpdate.isEmpty())
        {
            job.getLogger().info("Applying pedigree overrides, total: " + toUpdate.size());

            try
            {
                subjectsTable.getUpdateService().updateRows(job.getUser(), job.getContainer(), toUpdate, oldKeys, null, new HashMap<>());
            }
            catch (QueryException | BatchValidationException | SQLException | InvalidKeyException | QueryUpdateServiceException e)
            {
                throw new PipelineJobException(e);
            }
        }

        return new RecordedActionSet();
    }

    @Override
    public List<String> getRequiredSettings()
    {
        return Collections.emptyList();
    }

    @Override
    public void setSettings(Map<String, String> map) throws XmlException
    {

    }

    @Override
    public void setContainerUser(ContainerUser containerUser)
    {
        _containerUser = containerUser;
    }
}
