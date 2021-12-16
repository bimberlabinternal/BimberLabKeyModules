package org.labkey.mgap.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.template.ClientDependency;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.Set;

/**
 * Created by bimber on 5/17/2017.
 */
public class VariantReleaseDisplayColumnFactory implements DisplayColumnFactory
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

                keys.add(getBoundKey("rowid"));
                keys.add(getBoundKey("objectId"));
                keys.add(getBoundKey("version"));
                keys.add(getBoundKey("jbrowseId"));
                keys.add(getBoundKey("container"));
                keys.add(getBoundKey("hasSignificantVariants"));
            }

            private FieldKey getBoundKey(String colName)
            {
                return new FieldKey(getBoundColumn().getFieldKey().getParent(), colName);
            }

            @Override
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                Integer rowId = ctx.get(getBoundKey("rowid"), Integer.class);
                if (rowId != null)
                {
                    out.write("<a class=\"labkey-text-link\" href=\"javascript:void(0);\" onclick=\"mGAP.window.DownloadWindow.buttonHandler(" + PageFlowUtil.jsString(rowId.toString()) + ", this);\">Download</a>");
                }

                String jbrowseId = ctx.get(getBoundKey("jbrowseId"), String.class);
                String containerId = ctx.get(getBoundKey("container"), String.class);
                if (jbrowseId != null)
                {
                    if (rowId != null)
                    {
                        out.write("<br>");
                    }

                    DetailsURL url = DetailsURL.fromString("/jbrowse/browser.view?database=" + jbrowseId, ContainerManager.getForId(containerId));
                    out.write("<a class=\"labkey-text-link\" href=\"" + url.getActionURL().getURIString() + "\");\">View In Genome Browser</a>");
                }

                Boolean showVariantList = ctx.get(getBoundKey("hasSignificantVariants"), Boolean.class);
                if (showVariantList)
                {
                    out.write("<br>");

                    DetailsURL url = DetailsURL.fromString("/mgap/variantList.view?release=" + rowId, ContainerManager.getForId(containerId));
                    out.write("<a class=\"labkey-text-link\" href=\"" + url.getActionURL().getURIString() + "\");\">Significant Variant List</a>");
                }
            }

            @NotNull
            @Override
            public Set<ClientDependency> getClientDependencies()
            {
                return PageFlowUtil.set(ClientDependency.fromPath("Ext4"), ClientDependency.fromPath("mgap/DownloadWindow.js"));
            }

            @Override
            public boolean isFilterable()
            {
                return false;
            }

            @Override
            public boolean isSortable()
            {
                return false;
            }
        };
    }
}
