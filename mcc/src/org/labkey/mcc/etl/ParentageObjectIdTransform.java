package org.labkey.mcc.etl;

import org.labkey.api.di.columnTransform.ColumnTransform;

public class ParentageObjectIdTransform extends ColumnTransform
{
    @Override
    protected Object doTransform(Object inputValue)
    {
        if (inputValue == null)
        {
            return null;
        }

        String objectId = String.valueOf(inputValue);

        if (getInputValue("dam") != null)
        {
            objectId = objectId + "-Dam";
        }
        else if (getInputValue("sire") != null)
        {
            objectId = objectId + "-Sire";
        }

        return objectId;
    }
}
