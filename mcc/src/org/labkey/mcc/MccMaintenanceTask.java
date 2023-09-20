package org.labkey.mcc;

import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.SystemMaintenance;

import java.util.Set;

public class MccMaintenanceTask implements SystemMaintenance.MaintenanceTask
{
    @Override
    public String getDescription()
    {
        return "Perform maintenance and checks related to the MCC";
    }

    @Override
    public String getName()
    {
        return "MCC Maintenance";
    }

    @Override
    public void run(Logger log)
    {
        checkForDuplicateAliases(log);

    }

    private void checkForDuplicateAliases(Logger log)
    {
        Container studyParent = MccManager.get().getMCCInternalDataContainer(ContainerManager.getRoot());
        if (studyParent == null)
        {
            return;
        }

        Set<? extends Study> studies = StudyService.get().getAllStudies(studyParent);
        if (studies == null || studies.isEmpty())
        {
            return;
        }

        // Perform this check after the others. To get this far, an admin would need to set several properties, effectively opting-in to this code,
        // so it's reasonable to make this an error and not a warning
        User u = LDKService.get().getBackgroundAdminUser();
        if (u == null)
        {
            log.error("The LDK module property BackgroundAdminUser must be set for the MCC Maintenance code to function");
        }

        Container animalData = MccManager.get().getMCCContainer(ContainerManager.getRoot());
        if (animalData != null)
        {
            TableInfo ti = QueryService.get().getUserSchema(u, animalData, MccSchema.NAME).getTable("duplicatedAggregatedDemographicsParents");
            if (new TableSelector(ti).exists())
            {
                log.error("Duplicate MCC aliases for parents found. Please load the query mcc/duplicatedAggregatedDemographicsParents for more detail");
            }
        }

        for (Study s : studies)
        {
            if (!s.getContainer().getActiveModules().contains(ModuleLoader.getInstance().getModule(MccModule.NAME)))
            {
                continue;
            }

            TableInfo ti = QueryService.get().getUserSchema(u, s.getContainer(), MccSchema.NAME).getTable("duplicateAliases");
            if (new TableSelector(ti).exists())
            {
                log.error("Duplicate MCC aliases found in the folder: " + s.getContainer().getPath() + ". Please load the query mcc/duplicateAliases for more detail");
            }
        }
    }
}
