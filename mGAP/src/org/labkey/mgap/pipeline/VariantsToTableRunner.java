package org.labkey.mgap.pipeline;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractGatkWrapper;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class VariantsToTableRunner extends AbstractGatkWrapper
{
    public VariantsToTableRunner(Logger log)
    {
        super(log);
    }

    public File execute(File inputVcf, File outputTable, File fasta, List<String> fields)  throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.addAll(getBaseArgs());

        args.add("-T");
        args.add("VariantsToTable");

        args.add("-R");
        args.add(fasta.getPath());

        args.add("-V");
        args.add(inputVcf.getPath());

        args.add("-o");
        args.add(outputTable.getPath());

        args.add("-AMD");

        fields.forEach(x ->{
            args.add("-F");
            args.add(x);
        });
        execute(args);

        if (!outputTable.exists())
        {
            throw new PipelineJobException("Unable to find file: " + outputTable.getPath());
        }

        return outputTable;
    }
}
