package org.labkey.mgap.query;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Created by bimber on 5/17/2017.
 */
public class TracksPerReleaseGenomeBrowserDisplayColumnFactory extends VariantReleaseGenomeBrowserDisplayColumnFactory
{
    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new VariantReleaseGenomeBrowserDisplayColumnFactory.BrowserDataColumn(colInfo, PageFlowUtil.set("releaseId/jbrowseId", "releaseId/container", "jbrowseTracks"))
        {
            @Override
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                String jbrowseId = ctx.get(getBoundKey("releaseId", "jbrowseId"), String.class);
                String containerId = ctx.get(getBoundKey("releaseId", "container"), String.class);
                String jbrowseTracks = ctx.get(getBoundKey("jbrowseTracks"), String.class);

                if (jbrowseId != null && jbrowseTracks != null)
                {
                    //reverse order serves to put the 'track-' items (like built-in genes) ahead of the data- items (like VCFs)
                    List<String> tracks = new ArrayList<>(Arrays.asList(jbrowseTracks.split(",")));
                    Collections.sort(tracks, Collections.reverseOrder());

                    DetailsURL url = DetailsURL.fromString("/jbrowse/browser.view?database=" + jbrowseId + "&tracks=" + StringUtils.join(tracks, ","), ContainerManager.getForId(containerId));
                    out.write("<a class=\"labkey-text-link\" href=\"" + url.getActionURL().getURIString() + "\");\">View In Genome Browser</a>");
                }
            }
        };
    }
}
