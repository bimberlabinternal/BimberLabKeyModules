package org.labkey.htcondorconnector;

import org.labkey.api.htcondorconnector.HTCondorJobResourceAllocator;
import org.labkey.api.htcondorconnector.HTCondorService;
import org.labkey.api.pipeline.TaskId;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Created by bimber on 2/23/2016.
 */
public class HTCondorServiceImpl extends HTCondorService
{
    private List<HTCondorJobResourceAllocator> _allocatorList = new ArrayList<>();

    public static HTCondorServiceImpl get()
    {
        return (HTCondorServiceImpl)HTCondorService.get();
    }

    public HTCondorServiceImpl()
    {

    }

    public void registerResourceAllocator(HTCondorJobResourceAllocator allocator)
    {
        _allocatorList.add(allocator);
    }

    public HTCondorJobResourceAllocator getAllocator(TaskId taskId)
    {
        TreeMap<Integer, List<HTCondorJobResourceAllocator>> ret = new TreeMap<>();
        for (HTCondorJobResourceAllocator allocator : _allocatorList)
        {
            Integer priorty = allocator.getPriority(taskId);
            if (priorty != null)
            {
                if (!ret.containsKey(priorty))
                {
                    ret.put(priorty, new ArrayList<>());
                }

                ret.get(priorty).add(allocator);
            }
        }

        if (ret.isEmpty())
        {
            return null;
        }

        List<HTCondorJobResourceAllocator> highest = ret.get(ret.descendingKeySet().first());

        return highest.get(highest.size() - 1);
    }
}
