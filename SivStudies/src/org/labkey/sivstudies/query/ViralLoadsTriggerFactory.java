package org.labkey.sivstudies.query;

import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.triggers.Trigger;
import org.labkey.api.data.triggers.TriggerFactory;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ViralLoadsTriggerFactory implements TriggerFactory
{
    public ViralLoadsTriggerFactory()
    {

    }

    @Override
    public @NotNull Collection<Trigger> createTrigger(@Nullable Container c, TableInfo table, Map<String, Object> extraContext)
    {
        return List.of(new ViralLoadTrigger());
    }

    public static class ViralLoadTrigger implements Trigger
    {
        @Override
        public void beforeInsert(TableInfo table, Container c, User user, @Nullable Map<String, Object> newRow, ValidationException errors, Map<String, Object> extraContext) throws ValidationException
        {
            beforeInsert(table, c, user, newRow, errors, extraContext, null);
        }

        @Override
        public void beforeInsert(TableInfo table, Container c, User user, @Nullable Map<String, Object> newRow, ValidationException errors, Map<String, Object> extraContext, @Nullable Map<String, Object> existingRecord) throws ValidationException
        {
            handlePVL(newRow, c, errors);
        }

        @Override
        public void beforeUpdate(TableInfo table, Container c, User user, @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow, ValidationException errors, Map<String, Object> extraContext) throws ValidationException
        {
            handlePVL(newRow, c, errors);
        }

        /**
         * This allows incoming data to specify the study using the string name, which is resolved into the rowId
         */
        private void handlePVL(@Nullable Map<String, Object> row, Container c, ValidationException errors)
        {
            if (row == null)
            {
                return;
            }

            if (row.get("result") == null || NumberUtils.isCreatable(row.get("result").toString()))
            {
                return;
            }

            String val = row.get("result").toString();
            val = val.replaceAll(",", "");
            if (NumberUtils.isCreatable(val))
            {
                row.put("result", val);
            }
            else if (val.startsWith("Below"))
            {
                val = val.replace("Below ", "");
                if (NumberUtils.isCreatable(val))
                {
                    row.put("result", val);
                    row.put("resultOORIndicator", "<");
                    row.put("lod", val);
                }
            }

            if (!NumberUtils.isCreatable(val))
            {
                errors.addError(new SimpleValidationError("Non-numeric VL: "+ val, "result", ValidationException.SEVERITY.ERROR));
            }
        }
    }
}
