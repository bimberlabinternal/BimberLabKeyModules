package org.labkey.variantdb.run;

import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.Interval;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.CloseableTribbleIterator;
import htsjdk.tribble.bed.BEDCodec;
import htsjdk.tribble.bed.BEDFeature;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFFileReader;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by bimber on 2/25/2015.
 */
public class ImputationRunner
{
    private Map<String, List<Interval>> _denseIntervalMap;
    private Map<String, List<Interval>> _frameworkIntervalMap;
    private List<String> _frameworkMarkerNames;

    public ImputationRunner(File denseBedFile, File frameworkBedFile, Logger log) throws PipelineJobException
    {
        //first build list of dense intervals by chromosome
        _denseIntervalMap = new HashMap<>();
        try (AbstractFeatureReader reader = AbstractFeatureReader.getFeatureReader(denseBedFile.getPath(), new BEDCodec(), false))
        {
            try (CloseableTribbleIterator<BEDFeature> it = reader.iterator())
            {
                while (it.hasNext())
                {
                    BEDFeature f = it.next();
                    if (!_denseIntervalMap.containsKey(f.getChr()))
                    {
                        _denseIntervalMap.put(f.getChr(), new LinkedList<Interval>());
                    }

                    _denseIntervalMap.get(f.getChr()).add(new Interval(f.getChr(), f.getStart(), f.getEnd()));
                }
            }

            //NOTE: convert this to an ArrayList, since we are going to access specific positions downstream:
            for (String chr : _denseIntervalMap.keySet())
            {
                _denseIntervalMap.put(chr, new ArrayList<>(_denseIntervalMap.get(chr)));
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        //then framework
        _frameworkIntervalMap = new HashMap<>();
        _frameworkMarkerNames = new LinkedList<>();
        try (AbstractFeatureReader reader = AbstractFeatureReader.getFeatureReader(frameworkBedFile.getPath(), new BEDCodec(), false))
        {
            try (CloseableTribbleIterator<BEDFeature> it = reader.iterator())
            {
                while (it.hasNext())
                {
                    BEDFeature f = it.next();
                    if (!_frameworkIntervalMap.containsKey(f.getChr()))
                    {
                        _frameworkIntervalMap.put(f.getChr(), new LinkedList<Interval>());
                    }

                    _frameworkIntervalMap.get(f.getChr()).add(new Interval(f.getChr(), f.getStart(), f.getEnd()));
                    _frameworkMarkerNames.add(f.getChr() + "_" + f.getStart());
                }
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        //summarize distances
        for  (String chr : _denseIntervalMap.keySet())
        {
            double totalDist = 0.0;
            List<Interval> framework = _frameworkIntervalMap.get(chr);
            for (Interval dense : _denseIntervalMap.get(chr))
            {
                Interval previous = null;
                for (Interval f : framework)
                {
                    if (f.getStart() >= dense.getStart())
                    {
                        if (previous == null)
                        {
                            totalDist += dense.getStart() - f.getStart();
                        }
                        else
                        {
                            totalDist += Math.min(f.getStart() - dense.getStart(), f.getStart() - previous.getStart());
                        }

                        break;
                    }

                    previous = f;
                }
            }

            log.info("chr: " + chr + ", avg dense marker distance from framework: " + (totalDist / _denseIntervalMap.get(chr).size()));
        }
    }

    public void processSet(File vcf, File outDir, String outPrefix, Logger log, List<String> completeGenotypes, List<String> imputed, String callMethod) throws PipelineJobException, IOException
    {
        log.info("processing set: " + StringUtils.join(completeGenotypes, ", "));
        log.info("imputing: " + StringUtils.join(imputed, ", "));

        prepareResources(vcf, outDir, outPrefix, log, completeGenotypes, imputed, "dense", _denseIntervalMap);
        prepareResources(vcf, outDir, outPrefix, log, completeGenotypes, imputed, "framework", _frameworkIntervalMap);

        for (String chr : _frameworkIntervalMap.keySet())
        {
            log.info("processing chromosome: " + chr + " for gl_auto");
            File basedir = new File(outDir, chr);
            runGlAuto(basedir, new File(basedir, "framework.geno"), log);
            log.info("processing chromosome: " + chr + " with GIGI");
            GigiRunner gigi = new GigiRunner(log);
            File gigiParams = new File(basedir, "gigi.par");
            try (BufferedWriter paramWriter = new BufferedWriter(new FileWriter(gigiParams)))
            {
                paramWriter.write(new File(basedir, "../../../morgan.ped").getPath() + '\n');
                paramWriter.write(new File(basedir, "framework.IVs").getPath() + '\n');
                paramWriter.write("1000\n");
                paramWriter.write(new File(basedir, "framework_map.txt").getPath() + '\n');
                paramWriter.write(new File(basedir, "dense_map.txt").getPath() + '\n');
                paramWriter.write(new File(basedir, "dense.gigi.geno").getPath() + '\n');
                paramWriter.write(new File(basedir, "dense.afreq").getPath() + '\n');
                paramWriter.write(callMethod);
            }
            gigi.execute(gigiParams, gigiParams.getParentFile());
        }
    }

    private void runGlAuto(File basedir, File markerFile, Logger log) throws PipelineJobException, IOException
    {
        File seedFile = new File(basedir, "sampler.seed");
        try (BufferedWriter seedWriter = new BufferedWriter(new FileWriter(seedFile)))
        {
            seedWriter.write("set sampler seeds  0xb69cb2f5 0x562302c9\n");
        }

        File glAutoParams = new File(basedir, "glauto.par");
        try (BufferedWriter glautoWriter = new BufferedWriter(new FileWriter(glAutoParams)))
        {
            glautoWriter.write("input pedigree file '../../../morgan.ped'\n");
            glautoWriter.write("input marker data file '" + markerFile.getName() + "'\n");
            glautoWriter.write("input seed file 'sampler.seed'\n\n");

            glautoWriter.write("#output file:\n");
            glautoWriter.write("output extra file 'framework.IVs'\n");
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

        GLAutoRunner runner = new GLAutoRunner(log);
        runner.setWorkingDir(basedir);
        runner.execute(glAutoParams);
    }

    private void prepareResources(File vcf, File outDir, String outPrefix, Logger log, List<String> completeGenotypes, List<String> imputed, String markerType, Map<String, List<Interval>> intervalMap) throws PipelineJobException
    {        
        for (String chr : intervalMap.keySet())
        {
            log.info("processing chromosome: " + chr + ".  for marker set: " + markerType + ".  total intervals: " + intervalMap.get(chr).size());
            File basedir = new File(outDir, chr);
            if (!basedir.exists())
                basedir.mkdirs();

            //write the output to a set of files we wil later merge
            File markerFile = new File(basedir, outPrefix + "_markers.tmp1");
            File markerPosFile = new File(basedir, outPrefix + "_markers.tmp2");
            File frequencyFile = new File(basedir, outPrefix + "_markers.tmp3");
            File alleleFrequencyFile = new File(basedir, markerType + ".afreq");
            File mapFile = new File(basedir, markerType + "_map.txt");

            int idx = 1;
            try
            {
                Map<String, File> genoFileMap = new HashMap<>();
                Map<String, File> completeGenoFileMap = new HashMap<>();

                Map<String, BufferedWriter> genoWriterMap = new HashMap<>();
                Map<String, BufferedWriter> completeGenoWriterMap = new HashMap<>();
                try (
                        BufferedWriter markerLineWriter = new BufferedWriter(new FileWriter(markerFile));
                        BufferedWriter markerPosLineWriter = new BufferedWriter(new FileWriter(markerPosFile));
                        BufferedWriter frequencyWriter = new BufferedWriter(new FileWriter(frequencyFile));
                        BufferedWriter frequencyWriter2 = new BufferedWriter(new FileWriter(alleleFrequencyFile));
                        BufferedWriter mapWriter = new BufferedWriter(new FileWriter(mapFile))
                )
                {
                    markerLineWriter.write("set marker names");
                    markerPosLineWriter.write("map marker positions");

                    File vcfIdx = SequenceAnalysisService.get().ensureVcfIndex(vcf, log);
                    try (VCFFileReader reader = new VCFFileReader(vcf, vcfIdx, true))
                    {
                        try
                        {
                            for (String name : reader.getFileHeader().getSampleNamesInOrder())
                            {
                                File tmp = new File(basedir, "geno_" + name + ".tmp");
                                genoFileMap.put(name, tmp);

                                File tmp2 = new File(basedir, "genoComplete_" + name + ".tmp");
                                completeGenoFileMap.put(name, tmp2);

                                genoWriterMap.put(name, new BufferedWriter(new FileWriter(tmp)));
                                completeGenoWriterMap.put(name, new BufferedWriter(new FileWriter(tmp2)));
                            }

                            for (Interval i : intervalMap.get(chr))
                            {
                                try (CloseableIterator<VariantContext> it = reader.query(chr, i.getStart(), i.getEnd()))
                                {
                                    while (it.hasNext())
                                    {
                                        VariantContext ctx = it.next();
                                        String markerName = ctx.getChr() + "_" + ctx.getStart();

                                        mapWriter.write((ctx.getStart() / 1000000.0) + "\n");
                                        markerLineWriter.append(" ").append(markerName);
                                        markerPosLineWriter.append(" " + ctx.getStart() / 1000000.0);

                                        //TODO: smarter plan?
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

                                        DecimalFormat fmt = new DecimalFormat("0.000");
                                        fmt.setRoundingMode(RoundingMode.HALF_UP);

                                        String roundedTotal = fmt.format(1 - total);
                                        frequencyWriter.append("set markers " + idx + " allele freqs " + roundedTotal);
                                        frequencyWriter2.append(roundedTotal);
                                        for (String af : afs)
                                        {
                                            frequencyWriter.append(" ").append(af);
                                            frequencyWriter2.append(" ").append(af);
                                        }
                                        frequencyWriter.append("\n");
                                        frequencyWriter2.append("\n");

                                        idx++;
                                        if (idx % 1000000 == 0)
                                        {
                                            log.info("processed " + idx + " loci");
                                        }

                                        for (String name : ctx.getSampleNames())
                                        {
                                            BufferedWriter genoWriter = genoWriterMap.get(name);
                                            BufferedWriter completeGenoWriter = completeGenoWriterMap.get(name);
                                            Genotype g = ctx.getGenotype(name);
                                            if (g.getAlleles().size() != 2)
                                            {
                                                throw new PipelineJobException("More than 2 genotypes found for marker: " + ctx.getChr() + " " + ctx.getStart());
                                            }

                                            for (Allele a : g.getAlleles())
                                            {
                                                if (a.isCalled())
                                                {
                                                    Integer ai = ctx.getAlleleIndex(a) + 1;
                                                    if (ai == 1 && !a.isReference())
                                                    {
                                                        throw new PipelineJobException(ai + "/" + ctx.getStart() + "/" + a.getBaseString());
                                                    }
                                                    else if (ai > 2)
                                                    {
                                                        log.error(ai + "/" + ctx.getStart() + "/" + a.getBaseString());
                                                    }

                                                    completeGenoWriter.append(" ").append(a.isReference() ? "1" : ai.toString());

                                                    if (_frameworkMarkerNames.contains(markerName) || completeGenotypes.contains(g.getSampleName()))
                                                    {
                                                        genoWriter.append(" ").append(a.isReference() ? "1" : ai.toString());
                                                    }
                                                    else
                                                    {
                                                        genoWriter.append(" ").append("0");
                                                    }
                                                }
                                                else
                                                {
                                                    genoWriter.append(" ").append("0");
                                                    completeGenoWriter.append(" ").append("0");
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        finally
                        {
                            for (BufferedWriter w : genoWriterMap.values())
                            {
                                if (w != null)
                                {
                                    CloserUtil.close(w);
                                }
                            }

                            for (BufferedWriter w : completeGenoWriterMap.values())
                            {
                                if (w != null)
                                {
                                    CloserUtil.close(w);
                                }
                            }
                        }
                    }
                }

                //now write each version of the marker files for GIGI runs
                File finalMarkerFile = new File(basedir, markerType + ".geno");
                File gigiGenoFile = new File(basedir, markerType + ".gigi.geno");
                try (BufferedWriter markerWriter = new BufferedWriter(new FileWriter(finalMarkerFile));BufferedWriter gigiGenoWriter = new BufferedWriter(new FileWriter(gigiGenoFile)))
                {
                    for (File file : Arrays.asList(markerFile, markerPosFile, frequencyFile))
                    {
                        try (BufferedReader reader = new BufferedReader(new FileReader(file)))
                        {
                            IOUtils.copy(reader, markerWriter);
                            markerWriter.write("\n\n");
                        }

                        file.delete();
                    }

                    markerWriter.write("set marker " + (idx - 1) + " data\n");
                    for (String name : genoFileMap.keySet())
                    {
                        File file = genoFileMap.get(name);
                        if (completeGenotypes.contains(name) || imputed.contains(name))
                        {
                            try (BufferedReader reader = new BufferedReader(new FileReader(file)))
                            {
                                markerWriter.write(name + " ");
                                IOUtils.copy(reader, markerWriter);
                                markerWriter.write("\n");
                            }

                            try (BufferedReader reader = new BufferedReader(new FileReader(file)))
                            {
                                gigiGenoWriter.write(name + " ");
                                IOUtils.copy(reader, gigiGenoWriter);
                                gigiGenoWriter.write("\n");
                            }
                        }

                        file.delete();
                    }
                }

                File completeGenoFile = new File(basedir, markerType + "Complete.geno");
                try (BufferedWriter completeGenoWriter = new BufferedWriter(new FileWriter(completeGenoFile)))
                {
                    for (String name : completeGenoFileMap.keySet())
                    {
                        File file = completeGenoFileMap.get(name);
                        try (BufferedReader reader = new BufferedReader(new FileReader(file)))
                        {
                            completeGenoWriter.write(name + " ");
                            IOUtils.copy(reader, completeGenoWriter);
                            completeGenoWriter.write("\n");
                        }

                        file.delete();
                    }
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }

    private double round(double a)
    {
        return Math.round(a * 1000) / 1000.f;
    }

    public Set<String> getDenseChrs()
    {
        return _denseIntervalMap.keySet();
    }

    public List<Interval> getFrameworkIntervals(String chr)
    {
        return _frameworkIntervalMap.get(chr);
    }

    //0-based
    public Interval getDensePositionByIndex(String chr, int idx)
    {
        return _denseIntervalMap.get(chr).get(idx - 1);
    }
}
