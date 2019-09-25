package org.labkey.mgap.buttons;

import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.Module;
import org.labkey.api.view.template.ClientDependency;

public class ReleaseButton extends SimpleButtonConfigFactory
{
    public ReleaseButton(Module owner)
    {
        super(owner, "Create mGAP Release", "mGAP.window.ReleaseWindow.buttonHandler();", ClientDependency.fromList("laboratory.context;/SequenceAnalysis/window/OutputHandlerWindow.js;/mGAP/window/ReleaseWindow.js;sequenceanalysis/field/GenomeFileSelectorField.js"));
    }
}
