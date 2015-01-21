package org.labkey.variantdb.button;

import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.variantdb.VariantDBModule;

import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * Created by bimber on 1/5/2015.
 */
public class DbSnpLoadButton extends SimpleButtonConfigFactory
{
    public DbSnpLoadButton()
    {
        super(ModuleLoader.getInstance().getModule(VariantDBModule.class), "Import dbSNP Build", "VariantDB.window.DbSNPLoadWindow.buttonHandler();", new LinkedHashSet<>(Arrays.asList(ClientDependency.fromModuleName("ldk"), ClientDependency.fromModuleName("laboratory"), ClientDependency.fromPath("variantdb/window/DbSNPLoadWindow.js"))));
    }
}
