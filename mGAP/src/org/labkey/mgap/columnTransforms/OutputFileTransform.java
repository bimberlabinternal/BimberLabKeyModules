package org.labkey.mgap.columnTransforms;

/**
 * Created by bimber on 5/1/2017.
 */
public class OutputFileTransform extends AbstractVariantTransform
{
    @Override
    protected Object doTransform(Object inputValue)
    {
        if (null == inputValue)
            return null;

        return getOrCreateOutputFile(inputValue, getInputValue("objectId"));
    }
}
