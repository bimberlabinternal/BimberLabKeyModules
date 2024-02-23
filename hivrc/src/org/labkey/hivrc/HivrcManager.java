package org.labkey.hivrc;

public class HivrcManager
{
    private static final HivrcManager _instance = new HivrcManager();

    private HivrcManager()
    {

    }

    public static HivrcManager get()
    {
        return _instance;
    }
}