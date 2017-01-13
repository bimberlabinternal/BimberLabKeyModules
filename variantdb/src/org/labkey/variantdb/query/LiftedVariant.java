package org.labkey.variantdb.query;

import htsjdk.samtools.util.Interval;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.GUID;

/**
 * Created by bimber on 1/8/2015.
 */
public class LiftedVariant extends Variant
{
    private String _variantId;
    private int _chainFile;

    public LiftedVariant(Variant v, @Nullable Interval liftOver, int chainFileId)
    {
        setObjectid(new GUID().toString());

        if (liftOver != null)
        {
            setSequenceName(liftOver.getSequence());
            setStartPosition(liftOver.getStart());
            setEndPosition(liftOver.getEnd());
        }

        setReferenceVariantId(v.getReferenceVariantId());
        setReferenceAlleleId(v.getReferenceAlleleId());
        setStatus("Liftover");
        setVariantId(v.getObjectid());
        setChainFile(chainFileId);

        //setAllele(v.getAllele());
    }

    public String getVariantId()
    {
        return _variantId;
    }

    public void setVariantId(String variantId)
    {
        _variantId = variantId;
    }

    public int getChainFile()
    {
        return _chainFile;
    }

    public void setChainFile(int chainFile)
    {
        _chainFile = chainFile;
    }

    public boolean successfulLiftover()
    {
        return getSequenceName() != null;
    }
}
