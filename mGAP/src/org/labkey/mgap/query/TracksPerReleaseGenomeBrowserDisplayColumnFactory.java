package org.labkey.mgap.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

/**
 * Created by bimber on 5/17/2017.
 */
public class TracksPerReleaseGenomeBrowserDisplayColumnFactory extends VariantReleaseGenomeBrowserDisplayColumnFactory
{
    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new VariantReleaseGenomeBrowserDisplayColumnFactory.BrowserDataColumn(colInfo, PageFlowUtil.set("releaseId/jbrowseId", "releaseId/container", "trackName"))
        {
            @Override
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                String jbrowseId = ctx.get(getBoundKey("releaseId", "jbrowseId"), String.class);
                String containerId = ctx.get(getBoundKey("releaseId", "container"), String.class);
                String trackName = ctx.get(getBoundKey("trackName"), String.class);
                String species = ctx.get(getBoundKey("releaseId", "species"), String.class);

                if (jbrowseId != null && trackName != null)
                {
                    DetailsURL url = DetailsURL.fromString("/mgap/genomeBrowser.view?database=" + jbrowseId + "&activeTracks=" + trackName + (species == null ? "" : "&nhpSpecies=" + species), ContainerManager.getForId(containerId));
                    out.write("<a class=\"labkey-text-link\" href=\"" + url.getActionURL().getURIString() + "\");\">View In Genome Browser</a>");
                }
            }

            @Override
            public void addQueryFieldKeys(Set<FieldKey> keys)
            {
                super.addQueryFieldKeys(keys);

                keys.add(getBoundKey("species"));
                keys.add(getBoundKey("trackName"));
                keys.add(getBoundKey("releaseId", "jbrowseId"));
                keys.add(getBoundKey("releaseId", "container"));
                keys.add(getBoundKey("releaseId", "species"));
            }
        };
    }
}
