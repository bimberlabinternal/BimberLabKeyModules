package org.labkey.mgap.columnTransforms;

public class JBrowseHumanSessionTransform extends JBrowseSessionTransform
{
    @Override
    protected String getDataFileUrlField()
    {
        return "liftedVcfId/dataid/DataFileUrl";
    }

    @Override
    protected String getDatabaseName()
    {
        return "mGAP Release: " + getInputValue("version") + " Lifted To GRCh37";
    }

    @Override
    protected String getTrackDescription()
    {
        return "{\"category\":\"mGAP Variant Catalog\",\"visibleByDefault\": true,\"additionalFeatureMsg\":\"<h2>**These annotations are created by lifting the macaque variants to human coordinates, and must be viewed in that context.</h2>\"}";
    }
}
