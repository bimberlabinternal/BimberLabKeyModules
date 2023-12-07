package org.labkey.mgap;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.writer.PrintWriters;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class mGapMaintenanceTask implements SystemMaintenance.MaintenanceTask
{
    @Override
    public String getDescription()
    {
        return "Deletes unused mGAP VCFs and ETL artifacts";
    }

    @Override
    public String getName()
    {
        return "mGAP Maintenance Task";
    }

    @Override
    public void run(Logger log)
    {
        // Note: perform this check first as sort of an opt-in.
        // If this property is set, the admin should expect this to run and report errors if not completely configured.
        Container c = mGAPManager.get().getMGapContainer();
        if (c == null)
        {
            return;
        }

        User u = LDKService.get().getBackgroundAdminUser();
        if (u == null)
        {
            log.error("LDK Background user not set, cannot run mGAP Maintenance task");
            return;
        }

        checkForDuplicateAliases(log, u);
        checkMGapFiles(log, u);
    }

    private void checkMGapFiles(Logger log, User u)
    {
        Container c = mGAPManager.get().getMGapContainer();
        if (c == null)
        {
            return;
        }

        PipeRoot pr = PipelineService.get().getPipelineRootSetting(c);
        if (!pr.getRootPath().exists())
        {
            return;
        }

        File baseDir = new File(pr.getRootPath(), mGAPManager.DATA_DIR_NAME);
        if (!baseDir.exists())
        {
            return;
        }

        // Find expected folder names:
        List<String> releaseIds = new TableSelector(QueryService.get().getUserSchema(u, c, mGAPSchema.NAME).getTable(mGAPSchema.TABLE_VARIANT_CATALOG_RELEASES), PageFlowUtil.set("objectid")).getArrayList(String.class);

        Set<File> toDelete = new HashSet<>();

        File[] dirs = baseDir.listFiles((dir, name) -> dir.isDirectory());
        List<String> existingFolderNames = dirs == null ? Collections.emptyList() : Arrays.stream(dirs).map(File::getName).collect(Collectors.toList());
        List<String> unexpectedDirs = new ArrayList<>(existingFolderNames);
        unexpectedDirs.removeAll(releaseIds);
        for (String dirName : unexpectedDirs)
        {
            log.error("Unexpected directory present: " + dirName);
            toDelete.add(new File(baseDir, dirName));
        }

        List<String> missingDirs = new ArrayList<>(releaseIds);
        missingDirs.removeAll(existingFolderNames);
        for (String dirName : missingDirs)
        {
            log.error("Missing expected directory: " + dirName);
        }

        List<String> commandsToRun = new ArrayList<>();
        releaseIds.forEach(f -> inspectReleaseFolder(f, baseDir, c, u, log, toDelete, commandsToRun));

        // Also verify genomes:
        Set<Integer> genomesIds = new HashSet<>(new TableSelector(QueryService.get().getUserSchema(u, c, mGAPSchema.NAME).getTable(mGAPSchema.TABLE_VARIANT_CATALOG_RELEASES), PageFlowUtil.set("genomeId")).getArrayList(Integer.class));
        for (int genomeId : genomesIds)
        {
            try
            {
                ReferenceGenome rg = SequenceAnalysisService.get().getReferenceGenome(genomeId, u);
                checkSymlink(log, rg.getSourceFastaFile(), null, commandsToRun);
                checkSymlink(log, new File(rg.getSourceFastaFile().getPath() + ".fai"), null, commandsToRun);
                checkSymlink(log, new File(rg.getSourceFastaFile().getPath().replace(".fasta", ".dict")), null, commandsToRun);
            }
            catch (PipelineJobException e)
            {
                log.error("Error validating genome: " + genomeId, e);
            }
        }

        if (!commandsToRun.isEmpty())
        {
            log.error("There are missing symlinks. Please run makeSymlinks.sh");

            try (PrintWriter writer = PrintWriters.getPrintWriter(new File(baseDir, "makeSymlinks.sh")))
            {
                writer.println("#!/bin/bash");
                writer.println("set -e");
                writer.println("set -x");
                commandsToRun.forEach(writer::println);
            }
            catch (IOException e)
            {
                log.error("Error generating symlink script", e);
            }
        }
    }

    private void inspectReleaseFolder(String releaseId, File baseDir, Container c, User u, final Logger log, final Set<File> toDelete, List<String> commandsToRun)
    {
        File releaseDir = new File(baseDir, releaseId);
        if (!releaseDir.exists())
        {
            log.error("Missing folder: " + releaseDir.getPath());
            return;
        }

        final Set<File> expectedFiles = new HashSet<>();
        List<Integer> tracksFromRelease = new TableSelector(QueryService.get().getUserSchema(u, c, mGAPSchema.NAME).getTable(mGAPSchema.TABLE_TRACKS_PER_RELEASE), PageFlowUtil.set("vcfId"), new SimpleFilter(FieldKey.fromString("releaseId"), releaseId), null).getArrayList(Integer.class);
        tracksFromRelease.forEach(rowId -> {
            SequenceOutputFile so = SequenceOutputFile.getForId(rowId);
            if (so == null)
            {
                log.error("Unknown output file in tracksPerRelease: " + rowId + " for release: " + releaseId);
                return;
            }

            File f = so.getFile();
            if (f == null)
            {
                log.error("No file for outputfile: " + rowId + " for release: " + releaseId);
                return;
            }

            expectedFiles.add(f);
            checkSymlink(log, f, releaseId, commandsToRun);
            expectedFiles.add(new File(f.getPath() + ".tbi"));
            checkSymlink(log, new File(f.getPath() + ".tbi"), releaseId, commandsToRun);
        });

        final Set<String> fields = PageFlowUtil.set("vcfId", "variantTable", "liftedVcfId", "sitesOnlyVcfId", "novelSitesVcfId", "luceneIndex");
        new TableSelector(QueryService.get().getUserSchema(u, c, mGAPSchema.NAME).getTable(mGAPSchema.TABLE_VARIANT_CATALOG_RELEASES), fields, new SimpleFilter(FieldKey.fromString("objectid"), releaseId), null).forEachResults(rs -> {
            for (String field : fields)
            {
                if (rs.getObject(FieldKey.fromString(field)) == null)
                {
                    continue;
                }

                int rowId = rs.getInt(FieldKey.fromString(field));
                SequenceOutputFile so = SequenceOutputFile.getForId(rowId);
                if (so == null)
                {
                    log.error("Unknown output file: " + rowId + " for field: " + field + ", for variant release: " + releaseId);
                    continue;
                }

                File f = so.getFile();
                if (f == null)
                {
                    log.error("No file for outputfile: " + rowId + " for release: " + releaseId);
                    continue;
                }

                // NOTE: lucene points to a file, but the parent dir is what we need to manage:
                if ("write.lock".equals(f.getName()))
                {
                    expectedFiles.add(f.getParentFile().getParentFile());
                }

                expectedFiles.add(f);
                if (f.getPath().toLowerCase().endsWith("vcf.gz"))
                {
                    expectedFiles.add(new File(f.getPath() + ".tbi"));
                }
            }
        });

        File[] files = releaseDir.listFiles();
        List<File> filesPresent = files == null ? Collections.emptyList() : Arrays.asList(files);

        List<File> unexpectedFiles = new ArrayList<>(filesPresent);
        unexpectedFiles.removeAll(expectedFiles);
        for (File f : unexpectedFiles)
        {
            log.error("Unexpected file present: " + f.getPath());
            toDelete.add(f);
        }

        List<File> missingFiles = new ArrayList<>(expectedFiles);
        missingFiles.removeAll(filesPresent);
        for (File f : missingFiles)
        {
            // NOTE: the write.lock file is technically one directory lower and not caught with the check above
            if (!f.exists())
            {
                log.error("Missing expected file: " + f.getPath());
            }
        }
    }

    private void checkSymlink(Logger log, File f, @Nullable String dirName, List<String> commandsToRun)
    {
        File expectedSymlink = new File("/var/www/html/", (dirName == null ? "" : dirName + "/") + f.getName());
        if (dirName != null)
        {
            File expectedDir = new File("/var/www/html/", dirName);
            if (!expectedDir.exists())
            {
                log.error("Missing expected subdir: " + expectedDir);
                commandsToRun.add("mkdir -p " + expectedDir.getPath());
            }
        }

        if (!expectedSymlink.exists())
        {
            log.error("Missing symlink:  " + expectedSymlink.getPath());
            commandsToRun.add("ln -s " + f.getPath() + " " + expectedSymlink.getPath());
        }
    }

    private void checkForDuplicateAliases(Logger log, User u)
    {
        // Find all container Ids in use in the table:
        Set<String> containerIds = new HashSet<>(new TableSelector(mGAPSchema.getInstance().getSchema().getTable(mGAPSchema.TABLE_ANIMAL_MAPPING), PageFlowUtil.set("container")).getArrayList(String.class));
        for (String containerId : containerIds)
        {
            Container c = ContainerManager.getForId(containerId);
            if (c == null)
            {
                log.error("Unable to find container: " + containerId);
            }

            if (!c.getActiveModules().contains(ModuleLoader.getInstance().getModule(mGAPModule.class)))
            {
                continue;
            }

            TableInfo ti = QueryService.get().getUserSchema(u, c, mGAPSchema.NAME).getTable("duplicateAliases");
            if (new TableSelector(ti).exists())
            {
                log.error("Duplicate mGAP aliases found in the folder: " + c.getPath() + ". Please load the query mgap/duplicateAliases for more detail");
            }
        }
    }
}
