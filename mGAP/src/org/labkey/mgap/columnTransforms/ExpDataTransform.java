package org.labkey.mgap.columnTransforms;

import org.apache.commons.beanutils.ConversionException;
import org.apache.log4j.Logger;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.Results;
import org.labkey.api.data.Selector;
import org.labkey.api.data.StopIteratingException;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.di.columnTransform.AbstractColumnTransform;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.QueryService;
import org.labkey.api.util.PageFlowUtil;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by bimber on 5/1/2017.
 */
public class ExpDataTransform extends AbstractColumnTransform
{
    private static final Logger _log = Logger.getLogger(ExpDataTransform.class);

    @Override
    protected Object doTransform(Object inputValue)
    {
        if (null == inputValue)
            return null;

        try
        {
            URI uri = new URI(String.valueOf(inputValue));
            File f = new File(uri);
            if (!f.exists())
            {
                _log.error("File not found: " + uri.toString());
            }

            ExpData d = ExperimentService.get().getExpDataByURL(String.valueOf(inputValue), getContainerUser().getContainer());
            if (d == null)
            {
                d = ExperimentService.get().createData(getContainerUser().getContainer(), new DataType("Variant Catalog"));
                d.setDataFileURI(uri);
                d.setName(f.getName());
                d.save(getContainerUser().getUser());
            }

            return d.getRowId();
        }
        catch (URISyntaxException e)
        {
            _log.error("Error syncing file: " + String.valueOf(inputValue), e);
        }

        return null;
    }
}
