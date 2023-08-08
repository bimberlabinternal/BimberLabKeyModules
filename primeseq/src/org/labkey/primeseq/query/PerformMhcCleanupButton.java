package org.labkey.primeseq.query;

import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.primeseq.PrimeseqModule;

import java.util.Arrays;

public class PerformMhcCleanupButton extends SimpleButtonConfigFactory
{
    public PerformMhcCleanupButton()
    {
        super(ModuleLoader.getInstance().getModule(PrimeseqModule.class), "Perform MHC Cleanup", DetailsURL.fromString("primeseq/performMhcCleanup.view"));
    }
}