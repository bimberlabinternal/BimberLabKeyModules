package org.labkey.mgap.columnTransforms;

import org.labkey.api.data.Results;
import org.labkey.api.data.Selector;
import org.labkey.api.data.StopIteratingException;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.di.columnTransform.AbstractColumnTransform;
import org.labkey.api.query.QueryService;
import org.labkey.api.util.PageFlowUtil;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by bimber on 4/27/2017.
 */
public class GenomeTransform extends AbstractColumnTransform
{
    private Map<String, Integer> _genomeIdMap = null;

    @Override
    protected Object doTransform(Object inputValue)
    {
        if (null == inputValue)
            return null;

        return getGenomeIdMap().get(inputValue.toString());
    }

    private Map<String, Integer> getGenomeIdMap()
    {
        if (_genomeIdMap == null)
        {
            TableInfo ti = QueryService.get().getUserSchema(getContainerUser().getUser(), getContainerUser().getContainer(), "sequenceanalysis").getTable("sequence_libraries");
            final Map<String, Integer> genomeMap = new HashMap<>();
            new TableSelector(ti, PageFlowUtil.set("rowid", "name")).forEachResults(new Selector.ForEachBlock<Results>()
            {
                @Override
                public void exec(Results rs) throws SQLException, StopIteratingException
                {
                    genomeMap.put(rs.getString("name"), rs.getInt("rowid"));
                }
            });

            _genomeIdMap = genomeMap;
        }

        return _genomeIdMap;
    }

    @Override
    public void reset()
    {
        _genomeIdMap = null;
    }
}
