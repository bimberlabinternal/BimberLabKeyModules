package org.labkey.mgap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.DeferredUpgrade;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class mGapUpgradeCode implements UpgradeCode
{
    private static final Logger _log = LogManager.getLogger(mGapUpgradeCode.class);

    /** called at 16.60-16.61*/
    @SuppressWarnings({"UnusedDeclaration"})
    @DeferredUpgrade
    public void migrateReleaseDirs(final ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
            return;

        Container c = mGAPManager.get().getMGapContainer();
        if (c == null)
        {
            return;
        }

        final PipeRoot pr = PipelineService.get().getPipelineRootSetting(c);
        if (!pr.getRootPath().exists())
        {
            return;
        }

        final File baseDir = new File(pr.getRootPath(), mGAPManager.DATA_DIR_NAME);
        if (!baseDir.exists())
        {
            return;
        }

        User u = moduleContext.getUpgradeUser();

        new TableSelector(QueryService.get().getUserSchema(u, c, mGAPSchema.NAME).getTable(mGAPSchema.TABLE_VARIANT_CATALOG_RELEASES), PageFlowUtil.set("objectid")).forEachResults(rs -> {
            migrateRelease(c, u, rs.getString(FieldKey.fromString("objectid")), pr, baseDir);
        });
    }

    private void migrateRelease(Container c, User u, String releaseId, PipeRoot pr, File baseDir)
    {
        _log.info("Migrating release: " + releaseId);
        File previousDir = new File(pr.getRootPath(), releaseId);
        File newLocation = new File(baseDir, releaseId);

        if (!previousDir.exists())
        {
            _log.error("Missing release folder, cannot move directory: " + previousDir.getPath());
        }
        else if (newLocation.exists())
        {
            _log.error("Destination exists, cannot move mGAP release to: " + newLocation.getPath());
            return;
        }
        else
        {
            _log.info("Moving folder: " + previousDir.getPath());
            try
            {
                Files.move(previousDir.toPath(), newLocation.toPath());
            }
            catch (IOException e)
            {
                _log.error("Unable to move directory: " + previousDir.getPath(), e);
                return;
            }
        }

        // Now update any ExpDatas:
        List<? extends ExpData> datas = ExperimentService.get().getExpDatasUnderPath(previousDir, c);
        _log.info("Updating " + datas.size() + " expDatas for folder: " + previousDir.getPath());
        for (ExpData d : datas)
        {
            File oldFile = d.getFile();
            if (oldFile == null)
            {
                continue;
            }

            // Already pointing to the expected location:
            if (oldFile.getPath().contains("/" + mGAPManager.DATA_DIR_NAME + "/"))
            {
                _log.info("has already been migrated, skipping: " + oldFile.getPath());
                continue;
            }

            if (oldFile.exists())
            {
                _log.error("Expected old file to have been moved, but it exists: " + oldFile.getPath());
            }

            File newFile = new File(oldFile.getPath().replace("@files/", "@files/" + mGAPManager.DATA_DIR_NAME + "/"));
            if (!newFile.exists())
            {
                _log.error("Unable to find moved mGAP file: " + d.getRowId() + ", " + newFile.getPath());
            }
            else
            {
                d.setDataFileURI(newFile.toURI());
                d.save(u);
            }
        }
    }
}
