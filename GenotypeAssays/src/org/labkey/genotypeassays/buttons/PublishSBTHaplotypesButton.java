package org.labkey.genotypeassays.buttons;

import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.genotypeassays.GenotypeAssaysModule;

import java.util.Arrays;
import java.util.List;

/**
 * User: bimber
 * Date: 7/16/2014
 * Time: 5:37 PM
 */
public class PublishSBTHaplotypesButton extends SimpleButtonConfigFactory
{
    public PublishSBTHaplotypesButton()
    {
        super(ModuleLoader.getInstance().getModule(GenotypeAssaysModule.class), "Publish/Cache Selected", "GenotypeAssays.window.PublishResultsWindow.haplotypeButtonHandler(dataRegionName);", List.of(ClientDependency.supplierFromPath("genotypeassays/window/PublishResultsWindow.js")));
    }
}
