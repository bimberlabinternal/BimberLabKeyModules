package org.labkey.idr;

import org.labkey.api.action.SpringActionController;

public class IDRController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(IDRController.class);
    public static final String NAME = "idr";

    public IDRController()
    {
        setActionResolver(_actionResolver);
    }

}
