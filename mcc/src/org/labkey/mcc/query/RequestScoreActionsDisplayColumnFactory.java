package org.labkey.mcc.query;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.mcc.MccManager;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

public class RequestScoreActionsDisplayColumnFactory implements DisplayColumnFactory
{
    private static final Logger _log = LogManager.getLogger(RequestScoreActionsDisplayColumnFactory.class);

    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new AbstractMccDisplayColumn(colInfo)
        {
            @Override
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                int rowId = ctx.get(getBoundKey("rowid"), Integer.class);
                if (MccManager.get().isRequestAdmin(ctx.getViewContext().getUser()))
                {
                    int userId = ctx.get(getBoundKey("createdby"), Integer.class);
                    User u = UserManager.getUser(userId);
                    if (u == null)
                    {
                        _log.error("Unknown user: " + userId + " for MCC request " + rowId, new Exception());
                        return;
                    }

                    out.write("<a class=\"labkey-text-link\" href=\"mailto:" + u.getEmail() + "?subject=MCC Request #" + rowId + "\">Contact Investigator</a>");
                }

                String status = ctx.get(getBoundKey("requestId", "status"), String.class);
                if (status == null)
                {
                    _log.error("MCC Request lacks status: " + rowId, new Exception());
                }
                else
                {
                    try
                    {
                        String requestId = ctx.get(getBoundKey("requestId"), String.class);

                        MccManager.RequestStatus st = MccManager.RequestStatus.valueOf(status);
                        Container requestContainer = MccManager.get().getMCCRequestContainer();
                        if (requestContainer == null)
                        {
                            _log.error("RequestScoreActionsDisplayColumnFactory was called, but MCCRequestContainer is not set", new Exception());
                            return;
                        }

                        if (st == MccManager.RequestStatus.Submitted)
                        {
                            DetailsURL url = DetailsURL.fromString("/mcc/requestReview.view?requestId=" + requestId + "&mode=primaryReview", requestContainer);
                            out.write("<br><a class=\"labkey-text-link\" href=\"" + url.getActionURL() + "\">Enter MCC Internal Review</a>");
                        }
                        else if (st == MccManager.RequestStatus.FormCheck)
                        {
                            out.write("<br><a class=\"labkey-text-link\" href=\"javascript:void(0)\">Submit For RAB Review</a>");
                        }
                        else if (st == MccManager.RequestStatus.RabReview)
                        {
                            DetailsURL url = DetailsURL.fromString("/mcc/requestReview.view?requestId=" + requestId + "&mode=finalReview", requestContainer);
                            out.write("<br><a class=\"labkey-text-link\" href=\"" + url.getActionURL() + "\">Enter Final Review</a>");
                        }
                        else if (st == MccManager.RequestStatus.Approved)
                        {
                            out.write("<br><a class=\"labkey-text-link\" href=\"javascript:alert('This is not enabled yet')\">Update Animal Status</a>");
                        }
                    }
                    catch (IllegalArgumentException e)
                    {
                        _log.error("Unknown MCC Request status: " + rowId, new Exception());
                    }
                }
            }

            @Override
            public void addQueryFieldKeys(Set<FieldKey> keys)
            {
                super.addQueryFieldKeys(keys);

                keys.add(getBoundKey("rowid"));
                keys.add(getBoundKey("createdby"));
                keys.add(getBoundKey("requestId"));
                keys.add(getBoundKey("requestId/status"));
            }

            @Override
            public @NotNull Set<ClientDependency> getClientDependencies()
            {
                //TODO
                return super.getClientDependencies();
            }
        };
    }
}
