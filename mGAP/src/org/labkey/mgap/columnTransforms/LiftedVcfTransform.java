package org.labkey.mgap.columnTransforms;

public class LiftedVcfTransform extends OutputFileTransform
{
    @Override
    protected Object doTransform(Object inputValue)
    {
        if (null == inputValue)
            return null;

        return getOrCreateOutputFile(inputValue, getInputValue("objectId"), String.valueOf(getInputValue("liftedVcfId/name")));
    }

    @Override
    protected String getGenomeIdField()
    {
        return "liftedVcfId/library_id/name";
    }

    @Override
    protected String getDescription()
    {
        return "mGAP Release Lifted To Human";
    }
}
