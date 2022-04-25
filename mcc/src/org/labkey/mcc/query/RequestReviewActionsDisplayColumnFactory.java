package org.labkey.mcc.query;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

public class RequestReviewActionsDisplayColumnFactory implements DisplayColumnFactory
{
    private static final Logger _log = LogManager.getLogger(RequestReviewActionsDisplayColumnFactory.class);

    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new AbstractMccDisplayColumn(colInfo)
        {
            @Override
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                int userId = ctx.get(getBoundKey("reviewerId"), Integer.class);
                User u = UserManager.getUser(userId);
                if (u == null)
                {
                    _log.error("Unknown user: " + userId + " for MCC review record " + userId, new Exception());
                    return;
                }

                int rowId = ctx.get(getBoundKey("rowId"), Integer.class);
                String requestId = ctx.get(getBoundKey("requestId"), String.class);
                out.write("<a class=\"labkey-text-link\" onclick=\"MCC.window.EnterReviewWindow.buttonHandler(" + rowId + ", '" + requestId + "');\");\">Enter Review</a>");
            }

            @Override
            public void addQueryFieldKeys(Set<FieldKey> keys)
            {
                super.addQueryFieldKeys(keys);

                keys.add(getBoundKey("container"));
                keys.add(getBoundKey("requestid"));
                keys.add(getBoundKey("rowid"));
                keys.add(getBoundKey("reviewerId"));
            }
        };
    }
}
