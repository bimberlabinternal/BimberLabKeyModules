package org.labkey.mgap.jbrowse;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.jbrowse.JBrowseFieldCustomizer;
import org.labkey.api.jbrowse.JBrowseFieldDescriptor;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.mgap.mGAPModule;
import org.labkey.mgap.mGAPSchema;

import java.util.Arrays;
import java.util.List;

public class mGAPFieldCustomizer implements JBrowseFieldCustomizer
{
    private static final Logger _log = LogHelper.getLogger(mGAPFieldCustomizer.class, "Messages from mGAP Field Customizer");

    @Override
    public void customizeField(JBrowseFieldDescriptor field, Container c, User u)
    {
        List<AnnotationModel> ams = new TableSelector(QueryService.get().getUserSchema(u, c, mGAPSchema.NAME).getTable(mGAPSchema.TABLE_VARIANT_ANNOTATIONS), new SimpleFilter(FieldKey.fromString("infoKey"), field.getFieldName()), null).getArrayList(AnnotationModel.class);
        if (ams.isEmpty())
        {
            return;
        }
        else if (ams.size() > 1)
        {
            _log.error("Multiple annotation records found for the field: " + field.getFieldName());
        }

        AnnotationModel am = ams.get(0);
        if (am.isHidden())
        {
            field.setHidden(true);
        }

        if (am.isIndexed())
        {
            field.setIndexed(true);
        }

        if (StringUtils.trimToNull(am.getDescription()) != null)
        {
            field.setDescription(am.getDescription());
        }

        if (StringUtils.trimToNull(am.getLabel()) != null)
        {
            field.setLabel(am.getLabel());
        }

        if (StringUtils.trimToNull(am.getFormatString()) != null)
        {
            field.setFormatString(am.getFormatString());
        }

        if (StringUtils.trimToNull(am.getCategory()) != null)
        {
            field.setCategory(am.getCategory());
        }

        if (StringUtils.trimToNull(am.getAllowableValues()) != null)
        {
            field.setAllowableValues(Arrays.stream(am.getAllowableValues().split(",")).toList());
        }

        if (StringUtils.trimToNull(am.getUrl()) != null)
        {
            field.setUrl(am.getUrl());
        }


    }

    @Override
    public boolean isAvailable(Container c, User u)
    {
        return c.getActiveModules().contains(ModuleLoader.getInstance().getModule(mGAPModule.class));
    }
}
