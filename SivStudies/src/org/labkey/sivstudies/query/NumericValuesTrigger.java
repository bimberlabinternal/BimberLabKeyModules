package org.labkey.sivstudies.query;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.triggers.Trigger;
import org.labkey.api.data.triggers.TriggerFactory;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class NumericValuesTrigger extends DefaultDatasetTrigger
{
    public static class Factory implements TriggerFactory
    {
        // This map allows caller to supply a list of <StringValue> -> <TargetField>. If that string is found in a numeric field, it will be
        private final List<StringTransformer> _stringTransformers;

        public Factory()
        {
            this(null);
        }

        public Factory(@Nullable List<StringTransformer> stringTransformers)
        {
            _stringTransformers = stringTransformers == null ? Collections.emptyList() : stringTransformers;
        }

        @Override
        public @NotNull Collection<Trigger> createTrigger(@Nullable Container c, TableInfo table, Map<String, Object> extraContext)
        {
            return List.of(new NumericValuesTrigger(_stringTransformers));
        }
    }

    private final List<StringTransformer> _stringTransformers;

    public NumericValuesTrigger(List<StringTransformer> stringTransformers)
    {
        _stringTransformers = stringTransformers;
    }

    @Override
    protected void doBeforeUpsert(TableInfo table, Container c, User user, @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow, ValidationException errors, Map<String, Object> extraContext) throws ValidationException
    {
        inspectNumericValues(table, newRow, errors);
    }

    private void inspectNumericValues(TableInfo table, Map<String, Object> row, ValidationException errors)
    {
        for (String propName : row.keySet())
        {
            if (row.get(propName) == null)
            {
                continue;
            }

            ColumnInfo ci = table.getColumn(propName);
            if (ci == null)
            {
                continue;
            }

            if (!Number.class.isAssignableFrom(ci.getJdbcType().getJavaClass()))
            {
                continue;
            }

            String val = row.get(propName) == null ? null : String.valueOf(row.get(propName));
            if (val == null || NumberUtils.isCreatable(val))
            {
                continue;
            }

            // commas are a common problem:
            val = val.replaceAll(",", "");
            if (NumberUtils.isCreatable(val))
            {
                row.put(propName, val);
                continue;
            }

            // The Rlabkey API sending NAs as strings is another common problem:
            if ("NA".equalsIgnoreCase(val) || "null".equalsIgnoreCase(val) || StringUtils.trimToNull(val) == null)
            {
                row.put(propName, null);
                continue;
            }

            for (StringTransformer stringTransformer : _stringTransformers)
            {
                if (stringTransformer.inspectValue(table, row, val, propName, errors))
                {
                    // If the prior transformer edited the value, use this:
                    val = row.get(propName) == null ? null : String.valueOf(row.get(propName));
                    if (val == null)
                    {
                        break;
                    }
                }
            }

            if (val == null || NumberUtils.isCreatable(val))
            {
                row.put(propName, val);
                continue;
            }

            errors.addError(new SimpleValidationError("Non-numeric value for field " + propName + ": " + val, propName, ValidationException.SEVERITY.ERROR));
        }
    }

    public interface StringTransformer
    {
        // This method allows code to inspect and modify non-numeric values. Return true if the value was altered. If the value was altered, it should be stored in row[[propName]], and this value will be passed to the next transformer
        public boolean inspectValue(TableInfo ti, Map<String, Object> row, String stringValue, String propName, ValidationException errors);
    }
}
