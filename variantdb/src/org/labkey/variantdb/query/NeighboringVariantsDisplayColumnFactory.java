package org.labkey.variantdb.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.variantdb.VariantDBSchema;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

/**
 * User: bimber
 * Date: 7/22/2014
 * Time: 3:27 PM
 */
public class NeighboringVariantsDisplayColumnFactory implements DisplayColumnFactory
{
    public NeighboringVariantsDisplayColumnFactory()
    {

    }

    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        DataColumn ret = new DataColumn(colInfo)
        {
            @Override
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                Integer pos = (Integer)ctx.get("startPosition");
                if (pos != null)
                {
                    int start = pos - 50;
                    int end = pos + 50;

                    Integer sequenceId = (Integer)ctx.get("sequenceid");
                    ActionURL url = QueryService.get().urlFor(ctx.getViewContext().getUser(), ctx.getViewContext().getContainer(), QueryAction.executeQuery, VariantDBSchema.NAME, VariantDBSchema.TABLE_VARIANTS);
                    url.addParameter("query.startPosition~gte", start);
                    url.addParameter("query.startPosition~gte", end);
                    url.addParameter("query.sequenceid~eq", sequenceId);

                    out.write(PageFlowUtil.link("View Neighboring Variants").href(url).toString());
                }
                else
                {

                }
            }

            @Override
            public boolean isSortable()
            {
                return false;
            }

            @Override
            public boolean isFilterable()
            {
                return false;
            }

            @Override
            public boolean isEditable()
            {
                return false;
            }

            @Override
            public void addQueryFieldKeys(Set<FieldKey> keys)
            {
                super.addQueryFieldKeys(keys);

                keys.add(FieldKey.fromString("startPosition"));
                keys.add(FieldKey.fromString("endPosition"));
                keys.add(FieldKey.fromString("sequenceid"));
            }
        };

        ret.setCaption("");

        return ret;
    }
}
