package org.labkey.idr;

public class IDRManager
{
    private static final IDRManager _instance = new IDRManager();

    private IDRManager()
    {
        // prevent external construction with a private default constructor
    }

    public static IDRManager get()
    {
        return _instance;
    }
}