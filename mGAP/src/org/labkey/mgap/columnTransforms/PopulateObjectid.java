package org.labkey.mgap.columnTransforms;

import org.labkey.api.di.columnTransform.ColumnTransform;
import org.labkey.api.util.GUID;

public class PopulateObjectid extends ColumnTransform
{
    @Override
    protected Object doTransform(Object inputValue)
    {
        return new GUID().toString();
    }
}
