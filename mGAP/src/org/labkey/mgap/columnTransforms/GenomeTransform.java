package org.labkey.mgap.columnTransforms;

/**
 * Created by bimber on 4/27/2017.
 */
public class GenomeTransform extends AbstractVariantTransform
{
    @Override
    protected Object doTransform(Object inputValue)
    {
        if (null == inputValue)
            return null;

        return getGenomeIdMap().get(inputValue.toString());
    }
}
