package org.labkey.variantdb.run;

import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFFileReader;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 2/25/2015.
 */
public class VCFtoMorganConverter
{
    public VCFtoMorganConverter()
    {
        
    }
    
    public void convert(File vcf, File outDir, String basename, Logger log) throws PipelineJobException
    {
        //write the output to a set of files we wil later merge
        File markerLineTmp = new File(outDir, basename + "Markers.tmp1");
        File markerPosLineTmp = new File(outDir, basename + "Markers.tmp2");
        File frequencyTmp = new File(outDir, basename + "Markers.tmp3");
        int idx = 1;

        try (
                BufferedWriter markerLineWriter = new BufferedWriter(new FileWriter(markerLineTmp));
                BufferedWriter markerPosLineWriter = new BufferedWriter(new FileWriter(markerPosLineTmp));
                BufferedWriter frequencyWriter = new BufferedWriter(new FileWriter(frequencyTmp))
        )
        {
            markerLineWriter.write("set marker names");
            markerPosLineWriter.write("map marker positions");
            Map<String, BufferedWriter> genoMap = new HashMap<>();

            try (VCFFileReader reader = new VCFFileReader(vcf, false))
            {
                try
                {
                    for (String name : reader.getFileHeader().getSampleNamesInOrder())
                    {
                        genoMap.put(name, new BufferedWriter(new FileWriter(new File(outDir, "geno_" + name + ".tmp"))));
                    }

                    try (CloseableIterator<VariantContext> it = reader.iterator())
                    {

                        while (it.hasNext())
                        {
                            VariantContext ctx = it.next();
                            markerLineWriter.append(" ").append(ctx.getChr() + "_" + ctx.getStart());
                            markerPosLineWriter.append(" " + ctx.getStart() / 1000000.0);

                            if (ctx.getAttribute("AF") == null)
                            {
                                throw new PipelineJobException("No allele frequency found for marker: " + ctx.getChr() + " " + ctx.getStart());
                            }

                            List<String> afs;
                            if (ctx.getAttribute("AF") instanceof List)
                            {
                                afs = (List) ctx.getAttribute("AF");
                            }
                            else
                            {
                                afs = Arrays.asList(ctx.getAttributeAsString("AF", null).split(";"));
                            }

                            Double total = 0.0;
                            for (String d : afs)
                            {
                                total += Double.parseDouble(d);
                            }

                            frequencyWriter.append("set markers " + idx + " allele freqs " + (1 - total));
                            for (String af : afs)
                            {
                                frequencyWriter.append(" ").append(af).append("\n");
                            }
                            idx++;
                            if (idx % 1000000 == 0)
                            {
                                log.info("processed " + idx + " loci");
                            }

                            for (String name : ctx.getSampleNames())
                            {
                                BufferedWriter genoWriter = genoMap.get(name);
                                Genotype g = ctx.getGenotype(name);
                                if (g.getAlleles().size() != 2)
                                {
                                    throw new PipelineJobException("More than 2 genotypes found for marker: " + ctx.getChr() + " " + ctx.getStart());
                                }

                                for (Allele a : g.getAlleles())
                                {
                                    if (a.isCalled())
                                    {
                                        Integer ai = ctx.getAlleleIndex(a);
                                        genoWriter.append(" ").append(a.isReference() ? "1" : ai.toString());

                                    }
                                    else
                                    {
                                        genoWriter.append(" ").append("0");
                                    }
                                }
                            }
                        }
                    }
                }
                finally
                {
                    for (BufferedWriter w : genoMap.values())
                    {
                        if (w != null)
                        {
                            CloserUtil.close(w);
                        }
                    }

                }
            }

            //now write each version of the marker files for GIGI runs
            File markerFile = new File(outDir, SequencePipelineService.get().getUnzippedBaseName(vcf.getName()) + "." + basename + ".mark");
            try (BufferedWriter markerWriter = new BufferedWriter(new FileWriter(markerFile)))
            {
                for (File file : Arrays.asList(markerLineTmp, markerPosLineTmp, frequencyTmp))
                {
                    try (BufferedReader reader = new BufferedReader(new FileReader(file)))
                    {
                        IOUtils.copy(reader, markerWriter);
                        markerWriter.write("\n\n");
                    }

                    file.delete();
                }

                markerWriter.write("set marker " + idx + "data\n");
                for (String name : genoMap.keySet())
                {
                    File file = new File(outDir, "geno_" + name + ".tmp");
                    try (BufferedReader reader = new BufferedReader(new FileReader(file)))
                    {
                        markerWriter.write(name + " ");
                        IOUtils.copy(reader, markerWriter);
                        markerWriter.write("\n");
                    }

                    file.delete();
                }
            }

            File glAutoParams = new File(outDir, "glauto_" + basename + ".par");
            try (BufferedWriter glautoWriter = new BufferedWriter(new FileWriter(glAutoParams)))
            {
                glautoWriter.write("input pedigree file 'morgan.ped'\n");
                glautoWriter.write("input marker data file '" + markerFile.getName() + "'\n");
                glautoWriter.write("input seed file 'sampler.seed'\n\n");

                glautoWriter.write("#output file:\n");
                glautoWriter.write("output scores file 'framework.IVs'\n");
                glautoWriter.write("output meiosis indicators\n\n");

                glautoWriter.write("# Take care of pedigree order issues\n");
                glautoWriter.write("output pedigree file 'ordered.ped'\n\n");

                glautoWriter.write("check markers consistency\n\n");

                glautoWriter.write("########## other gl_auto program options #############\n");
                glautoWriter.write("# scoring:  We keep MCMC samples that are less correlated (every 30th).\n");
                glautoWriter.write("output scores every 30 scored MC iterations   # these are the realized IVs - In this example, we will print 300/30 = 100 IVs to the output file\n");
                glautoWriter.write("set MC iterations 30000\n");
                glautoWriter.write("set burn-in iterations 1000\n");

                glautoWriter.write("select all markers\n");
                glautoWriter.write("select trait 1\n");

                glautoWriter.write("use multiple meiosis sampler\n");
                glautoWriter.write("set limit for exact computation 12\n");

                glautoWriter.write("# Monte Carlo setup and requests\n");
                glautoWriter.write("use sequential imputation for setup\n");
                glautoWriter.write("sample by scan\n");
                glautoWriter.write("set L-sampler probability 0.5\n\n");


                glautoWriter.write("##################################################################\n");
                glautoWriter.write("#these dummy lines are here just so the program will run\n");
                glautoWriter.write("#the inference of IVs doesn't have anything to do with the trait\n");
                glautoWriter.write("#just include these lines:\n");
                glautoWriter.write("#trait\n");
                glautoWriter.write("#tloc 11 is just a name\n");
                glautoWriter.write("set trait 1  tloc 11\n");
                glautoWriter.write("map tloc 11 unlinked   # trait locus is unlinked\n");
                glautoWriter.write("set tloc 11 allele freqs 0.5 0.5\n");
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }
}
