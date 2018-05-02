package org.labkey.mgap.query;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Set;

public class VariantListJBrowseDisplayColumnFactory implements DisplayColumnFactory
{
    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new DataColumn(colInfo)
        {
            @Override
            public void addQueryFieldKeys(Set<FieldKey> keys)
            {
                super.addQueryFieldKeys(keys);
                keys.add(getBoundKey("releaseId/jbrowseId"));
                keys.add(getBoundKey("container"));
                keys.add(getBoundKey("contig"));
                keys.add(getBoundKey("position"));
            }

            private FieldKey getBoundKey(String colName)
            {
                FieldKey ret = getBoundColumn().getFieldKey().getParent();
                List<String> parts = FieldKey.fromString(colName).getParts();
                for (String part : parts)
                {
                    ret = new FieldKey(ret, part);
                }

                return ret;
            }

            @Override
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                String jbrowseId = StringUtils.trimToNull(ctx.get(getBoundKey("releaseId/jbrowseId"), String.class));
                String containerId = ctx.get(getBoundKey("container"), String.class);
                String contig = StringUtils.trimToNull(ctx.get(getBoundKey("contig"), String.class));
                Integer position = ctx.get(getBoundKey("position"), Integer.class);
                if (jbrowseId != null)
                {
                    int start = position - 1000;
                    int stop = position + 1000;

                    DetailsURL url = DetailsURL.fromString("/jbrowse/browser.view?database=" + jbrowseId + "&loc=" + contig + ":" + start + ".." + stop, ContainerManager.getForId(containerId));
                    out.write("<a class=\"labkey-text-link\" href=\"" + url.getActionURL().getURIString() + "\");\">View In Genome Browser</a>");
                }
            }
        };
    }
}
