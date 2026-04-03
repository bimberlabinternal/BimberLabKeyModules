package org.labkey.sivstudies.query;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.triggers.Trigger;
import org.labkey.api.data.triggers.TriggerFactory;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.logging.LogHelper;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DefaultDatasetTrigger implements Trigger
{
    protected static final Logger _log = LogHelper.getLogger(DefaultDatasetTrigger.class, "Messages related to DefaultDatasetTrigger");

    public static class Factory implements TriggerFactory
    {
        public Factory()
        {

        }

        @Override
        public @NotNull Collection<Trigger> createTrigger(@Nullable Container c, TableInfo table, Map<String, Object> extraContext)
        {
            return List.of(new DefaultDatasetTrigger());
        }

    }

    @Override
    public void beforeInsert(TableInfo table, Container c, User user, @Nullable QueryUpdateService.InsertOption insertOption, @Nullable Map<String, Object> newRow, ValidationException errors, Map<String, Object> extraContext) throws ValidationException
    {
        beforeInsert(table, c, user, insertOption, newRow, errors, extraContext, null);
    }

    @Override
    public void afterInsert(TableInfo table, Container c, User user, @Nullable Map<String, Object> newRow, ValidationException errors, Map<String, Object> extraContext, @Nullable Map<String, Object> existingRecord) throws ValidationException
    {
        afterUpsert(table, c, user, newRow, existingRecord, errors, extraContext);
    }

    @Override
    public void afterInsert(TableInfo table, Container c, User user, @Nullable Map<String, Object> newRow, ValidationException errors, Map<String, Object> extraContext) throws ValidationException
    {
        afterInsert(table, c, user, newRow, errors, extraContext, null);
    }

    @Override
    public void afterUpdate(TableInfo table, Container c, User user, @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow, ValidationException errors, Map<String, Object> extraContext) throws ValidationException
    {
        afterUpsert(table, c, user, newRow, oldRow, errors, extraContext);
    }

    protected void afterUpsert(TableInfo table, Container c, User user, @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow, ValidationException errors, Map<String, Object> extraContext) throws ValidationException
    {

    }

    @Override
    public void beforeInsert(TableInfo table, Container c, User user, @Nullable QueryUpdateService.InsertOption insertOption, @Nullable Map<String, Object> newRow, ValidationException errors, Map<String, Object> extraContext, @Nullable Map<String, Object> existingRecord) throws ValidationException
    {
        beforeUpsert(table, c, user, newRow, existingRecord, errors, extraContext);
    }

    @Override
    public void beforeUpdate(TableInfo table, Container c, User user, @Nullable QueryUpdateService.InsertOption insertOption, @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow, ValidationException errors, Map<String, Object> extraContext) throws ValidationException
    {
        beforeUpsert(table, c, user, newRow, oldRow, errors, extraContext);
    }

    private void beforeUpsert(TableInfo table, Container c, User user, @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow, ValidationException errors, Map<String, Object> extraContext) throws ValidationException
    {
        if (newRow == null)
        {
            _log.error("newRow was null. Unsure when this would ever happen", new Exception());
            return;
        }

        // Simplify properties:
        mergeOldToNewRow(newRow, oldRow);

        doBeforeUpsert(table, c, user, newRow, oldRow, errors, extraContext);
    }

    protected void doBeforeUpsert(TableInfo table, Container c, User user, @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow, ValidationException errors, Map<String, Object> extraContext) throws ValidationException
    {
        // Allow subclasses to implement code here
    }

    private void mergeOldToNewRow(@NotNull Map<String, Object> newRow, @Nullable Map<String, Object> oldRow) throws ValidationException
    {
        if (oldRow != null)
        {
            for (String propName : oldRow.keySet())
            {
                if (!newRow.containsKey(propName) & oldRow.get(propName) != null)
                {
                    newRow.put(propName, oldRow.get(propName));
                }
            }
        }
    }

    protected Container getTargetContainer(Container c)
    {
        return c.isWorkbookOrTab() ? c.getParent() : c;
    }

}
