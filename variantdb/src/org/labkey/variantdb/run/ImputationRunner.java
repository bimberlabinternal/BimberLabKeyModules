package org.labkey.variantdb.run;

import com.drew.lang.annotations.Nullable;
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
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.util.Compress;
import org.labkey.api.util.Pair;

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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
    private Map<String, List<List<Interval>>> _denseIntervalMapBatched;
    private Map<String, List<Interval>> _frameworkIntervalMap;
    private Map<String, List<List<List<String>>>> _denseMarkerAlleles;
    private Map<String, List<List<String>>> _frameworkMarkerAlleles;
    private Map<String, List<Interval>> _genotypeBlacklist;

    private List<String> _frameworkMarkerNames;
    private int _minGenotypeQual = 5;
    private int _minGenotypeDepth = 0;

    private int _denseMarkerBatchSize = 5000;

    public ImputationRunner(File denseBedFile, File frameworkBedFile, @Nullable File genotypeBlacklist, Logger log) throws PipelineJobException
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

        //then batch dense into subsets by batch size
        _denseIntervalMapBatched = new HashMap<>();
        for (String chr : _denseIntervalMap.keySet())
        {
            if (!_denseIntervalMapBatched.containsKey(chr))
            {
                _denseIntervalMapBatched.put(chr, new LinkedList<List<Interval>>());
            }

            int start = 0;
            int size = _denseIntervalMap.get(chr).size();
            while (start < size)
            {
                _denseIntervalMapBatched.get(chr).add(_denseIntervalMap.get(chr).subList(start, Math.min(size, start + _denseMarkerBatchSize)));
                start = start + _denseMarkerBatchSize;
            }
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

        //blacklist
        _genotypeBlacklist = new HashMap<>();
        if (genotypeBlacklist != null)
        {
            try (AbstractFeatureReader reader = AbstractFeatureReader.getFeatureReader(genotypeBlacklist.getPath(), new BEDCodec(), false))
            {
                try (CloseableTribbleIterator<BEDFeature> it = reader.iterator())
                {
                    while (it.hasNext())
                    {
                        BEDFeature f = it.next();
                        if (!_genotypeBlacklist.containsKey(f.getChr()))
                        {
                            _genotypeBlacklist.put(f.getChr(), new LinkedList<Interval>());
                        }

                        _genotypeBlacklist.get(f.getChr()).add(new Interval(f.getChr(), f.getStart(), f.getEnd()));
                    }
                }

                //NOTE: convert this to an ArrayList, since we are going to access specific positions downstream:
                for (String chr : _genotypeBlacklist.keySet())
                {
                    _genotypeBlacklist.put(chr, new ArrayList<>(_genotypeBlacklist.get(chr)));
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        //summarize distances
        for  (String chr : _denseIntervalMap.keySet())
        {
            double totalDist = 0.0;
            double denseTotalDist = 0.0;
            Interval previousDense = null;
            List<Interval> framework = _frameworkIntervalMap.get(chr);
            for (Interval dense : _denseIntervalMap.get(chr))
            {
                Interval previous = null;
                for (Interval f : framework)
                {
                    //track for dense
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

                if (previousDense != null)
                {
                    denseTotalDist += dense.getStart() - previousDense.getStart();
                }
                else
                {
                    denseTotalDist += dense.getStart();
                }

                previousDense = dense;
            }

            //and framework spacing
            Interval previous = null;
            double frameworkTotalDist = 0.0;
            for (Interval f : framework)
            {
                if (previous != null)
                {
                    frameworkTotalDist += f.getStart() - previous.getStart();
                }
                else
                {
                    frameworkTotalDist += f.getStart();
                }

                previous = f;
            }

            log.info("using batch size: " + _denseMarkerBatchSize);
            log.info("chr: " + chr + ", avg dense marker distance from framework: " + (totalDist / _denseIntervalMap.get(chr).size()));
            log.info("chr: " + chr + ", total framework markers: " + _frameworkIntervalMap.get(chr).size());
            log.info("chr: " + chr + ", total dense markers: " + _denseIntervalMap.get(chr).size() + ", batches: " + _denseIntervalMapBatched.get(chr).size());
            log.info("chr: " + chr + ", avg framework marker spacing: " + (frameworkTotalDist/ _frameworkIntervalMap.get(chr).size()));
            log.info("chr: " + chr + ", avg dense marker spacing: " + (denseTotalDist/ _denseIntervalMap.get(chr).size()));
        }
    }

    public void processSet(File outDir, File rawDataDir, Logger log, List<Pair<Integer, String>> completeGenotypes, List<Pair<Integer, String>> imputed, String callMethod) throws PipelineJobException, IOException
    {
        log.info("processing set, complete genotypes:");
        StringBuilder sb = new StringBuilder();
        String delim = "";
        for (Pair<Integer, String> pair : completeGenotypes)
        {
            sb.append(delim).append(pair.first).append("||").append(pair.second);
            delim = ";";
        }
        log.info(sb.toString());

        sb = new StringBuilder();
        sb.append("imputing: ");
        delim = "";
        for (Pair<Integer, String> pair : imputed)
        {
            sb.append(delim).append(pair.first).append("||").append(pair.second);
            delim = ";";
        }
        log.info(sb.toString());

        for (String chr : _frameworkIntervalMap.keySet())
        {
            log.info("processing chromosome: " + chr + " for gl_auto");
            File basedir = new File(outDir, chr);

            File ivFile = new File(basedir, "framework.IVs");
            if (!ivFile.exists())
            {
                runGlAuto(basedir, new File(basedir, "framework.glauto.geno"), log);
            }
            else
            {
                log.info("IV file exists, skipping GL_AUTO");
            }

            int denseMarkerIdx = -1;
            for (List<Interval> il : _denseIntervalMapBatched.get(chr))
            {
                denseMarkerIdx++;
                log.info("processing chromosome: " + chr + " with GIGI for batch: " + denseMarkerIdx + " of " + _denseIntervalMapBatched.get(chr).size());

                File imputeOutput = new File(basedir, "impute-" + denseMarkerIdx + ".geno");
                if (imputeOutput.exists())
                {
                    log.info("GIGI output already exists for batch, skipping: " + imputeOutput.getPath());
                    continue;
                }

                GigiRunner gigi = new GigiRunner(log);
                File gigiParams = new File(basedir, "gigi-" + denseMarkerIdx + ".par");
                try (BufferedWriter paramWriter = new BufferedWriter(new FileWriter(gigiParams)))
                {
                    File orderedPed = new File(basedir, "ordered.ped");
                    if (orderedPed.exists())
                    {
                        log.info("using ordered.ped file created by gl_auto");
                        File ped = preparePedigreeForGigi(orderedPed);
                        paramWriter.write(ped.getPath() + '\n');
                    }
                    else
                    {
                        paramWriter.write(new File(basedir, "../../morgan.ped").getPath() + '\n');
                    }
                    paramWriter.write(ivFile.getPath() + '\n');
                    paramWriter.write("1000\n");
                    paramWriter.write(new File(basedir, MarkerType.framework.name() + "_map.txt").getPath() + '\n');
                    paramWriter.write(new File(basedir, MarkerType.dense.name() + "-" + denseMarkerIdx + "_map.txt").getPath() + '\n');
                    paramWriter.write(new File(basedir, MarkerType.dense.name() + "-" + denseMarkerIdx + ".gigi.geno").getPath() + '\n');

                    File afreq = getAlleleFreqFile(rawDataDir, MarkerType.dense, chr, denseMarkerIdx);
                    paramWriter.write(afreq.getPath() + '\n');
                    paramWriter.write(callMethod);
                }

                gigi.execute(gigiParams, gigiParams.getParentFile());

                File toRename = new File(basedir, "impute" + ".geno");
                if (toRename.exists())
                {
                    log.info("renaming GIGI output: " + toRename.getPath());
                    FileUtils.moveFile(toRename, imputeOutput);
                }
            }
        }
    }

    /**
     * If we supply an input pedigree that is not sorted as GL_AUTO expects, it will output a new ordered.ped file.
     * Unfortunately, this pedigree doesnt conform to what GIGI expects, so we need to tweak the header here
     */
    private File preparePedigreeForGigi(File pedigree) throws IOException
    {
        File output = new File(pedigree.getParent(), "orderedGigi.ped");
        try (BufferedReader reader = new BufferedReader(new FileReader(pedigree));BufferedWriter writer = new BufferedWriter(new FileWriter(output)))
        {
            boolean inHeader = true;
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null)
            {
                lineNo++;
                if (inHeader)
                {
                    if (lineNo <= 3)
                    {
                        writer.write(line + '\n');
                    }

                    if (line.startsWith("*"))
                    {
                        inHeader = false;
                        writer.write(line + '\n');
                    }
                }
                else
                {
                    writer.write(line + '\n');
                }
            }
        }

        return output;
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
            glautoWriter.write("input pedigree file '../../morgan.ped'\n");
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

        File orderedPed = new File(basedir, "ordered.ped");
        if (orderedPed.exists())
        {
            orderedPed.delete();

        }
        GLAutoRunner runner = new GLAutoRunner(log);
        runner.setWorkingDir(basedir);
        runner.execute(glAutoParams);
    }

    public Map<String, List<Interval>> getDenseIntervalMap()
    {
        return Collections.unmodifiableMap(_denseIntervalMap);
    }

    public Map<String, List<List<Interval>>> getDenseIntervalMapBatched()
    {
        return Collections.unmodifiableMap(_denseIntervalMapBatched);
    }

    public Map<String, List<Interval>> getFrameworkIntervalMap()
    {
        return Collections.unmodifiableMap(_frameworkIntervalMap);
    }

    public File getAlleleFreqFile(File basedir, MarkerType markerType, String chr, @Nullable Integer denseMarkerBatchIdx)
    {
        return new File(basedir, markerType.name() + "." +  chr + (denseMarkerBatchIdx == null ? "" : "-" + denseMarkerBatchIdx) + ".markerFreq.txt");
    }

    private File getAlleleFreqGenotypesFile(File basedir, MarkerType markerType, String chr, @Nullable Integer denseMarkerBatchIdx)
    {
        return new File(basedir, markerType.name() + "." + chr + (denseMarkerBatchIdx == null ? "" : "-" + denseMarkerBatchIdx) + ".afreq");
    }

    public void prepareFrequencyFiles(File alleleFreqVcf, File rawDataDir, Logger log) throws PipelineJobException
    {
        //first dense
        _denseMarkerAlleles = new HashMap<>();
        for (String chr : _denseIntervalMapBatched.keySet())
        {
            _denseMarkerAlleles.put(chr, new ArrayList<List<List<String>>>());

            int denseMarkerIdx = -1;
            for (List<Interval> il : _denseIntervalMapBatched.get(chr))
            {
                denseMarkerIdx++;

                List<List<String>> markerAlleleList = new ArrayList<>();
                _denseMarkerAlleles.get(chr).add(markerAlleleList);
                prepareFrequencyFilesForChr(alleleFreqVcf, MarkerType.dense, chr, denseMarkerIdx, rawDataDir, il, markerAlleleList, log);
            }
        }

        //then framework
        _frameworkMarkerAlleles = new HashMap<>();
        for (String chr : _frameworkIntervalMap.keySet())
        {
            List<List<String>> markerAlleleList = new ArrayList<>();
            _frameworkMarkerAlleles.put(chr, markerAlleleList);

            prepareFrequencyFilesForChr(alleleFreqVcf, MarkerType.framework, chr, null, rawDataDir, _frameworkIntervalMap.get(chr), markerAlleleList, log);
        }
    }

    private void prepareFrequencyFilesForChr(File alleleFreqVcf, MarkerType markerType, String chr, @Nullable Integer denseMarkerBatchIdx, File rawDataDir, List<Interval> intervalList, List<List<String>> markerList, Logger log) throws PipelineJobException
    {
        log.info("preparing " + markerType.name() + " frequency files for: " + chr + (denseMarkerBatchIdx == null ? "" : ", batch: " + denseMarkerBatchIdx));
        File frequencyFile = getAlleleFreqGenotypesFile(rawDataDir, markerType, chr, denseMarkerBatchIdx);
        File alleleFrequencyFile = getAlleleFreqFile(rawDataDir, markerType, chr, denseMarkerBatchIdx);

        //note: dont shortcut this step on resume

        try (BufferedWriter frequencyWriter = new BufferedWriter(new FileWriter(frequencyFile)); BufferedWriter frequencyWriter2 = new BufferedWriter(new FileWriter(alleleFrequencyFile)))
        {
            int idx = 0;
            for (Interval i : intervalList)
            {
                idx++;
                if (idx % 1000 == 0)
                {
                    log.info("processed " + idx + " loci");
                }

                File vcfIdx = SequenceAnalysisService.get().ensureVcfIndex(alleleFreqVcf, log);
                try (VCFFileReader reader = new VCFFileReader(alleleFreqVcf, vcfIdx, true))
                {
                    try (CloseableIterator<VariantContext> it = reader.query(chr, i.getStart(), i.getEnd()))
                    {
                        String markerName = i.getSequence() + "_" + i.getStart();
                        if (!it.hasNext())
                        {
                            throw new PipelineJobException("position not found: " + markerName + " for allele frequencies in file: "+ alleleFreqVcf.getName());
                        }

                        while (it.hasNext())
                        {
                            VariantContext ctx = it.next();
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

                            DecimalFormat fmt = new DecimalFormat("0.000");
                            fmt.setRoundingMode(RoundingMode.HALF_UP);

                            Double totalNonRef = 0.0;
                            List<String> nonRefs = new ArrayList<>();
                            for (String d : afs)
                            {
                                Double dd = Double.parseDouble(d);
                                if (dd == 1.0)
                                {
                                    dd = 0.999;
                                }

                                totalNonRef += dd;
                                nonRefs.add(fmt.format(dd));
                            }

                            String roundedTotalRef = fmt.format(1 - totalNonRef);
                            frequencyWriter.append("set markers " + idx + " allele freqs " + roundedTotalRef);
                            frequencyWriter2.append(roundedTotalRef);
                            for (String af : nonRefs)
                            {
                                frequencyWriter.append(" ").append(af);
                                frequencyWriter2.append(" ").append(af);
                            }
                            frequencyWriter.append("\n");
                            frequencyWriter2.append("\n");

                            List<String> alleles = new ArrayList<>();
                            for (Allele a : ctx.getAlleles())
                            {
                                alleles.add(a.getBaseString());
                            }
                            if (!alleles.contains(ctx.getReference().getBaseString()))
                            {
                                log.warn("adding reference: " + markerName);
                                alleles.add(0, ctx.getReference().getBaseString());
                            }

                            if (!alleles.get(0).equals(ctx.getReference().getBaseString()))
                            {
                                throw new PipelineJobException("first allele is non-reference: " + markerName + "/" + ctx.getReference().getBaseString() + "/" + StringUtils.join(alleles, ";"));
                            }

                            markerList.add(alleles);
                        }
                    }
                }
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    public void prepareDenseGenotypeFiles(File vcf, GiGiType giGiType, File outputDir, Logger log, List<String> imputed, File gatkPed) throws PipelineJobException
    {
        for (String chr : _denseIntervalMapBatched.keySet())
        {
            log.info("processing chromosome: " + chr + ".  for marker set: dense.  to build data of type: " + giGiType.name() + ".  total batches: " + _denseIntervalMapBatched.get(chr).size());
            int denseMarkerBatchIdx = -1;
            for (List<Interval> il : _denseIntervalMapBatched.get(chr))
            {
                denseMarkerBatchIdx++;
                log.info("processing batch: " + denseMarkerBatchIdx + " of " + _denseIntervalMapBatched.get(chr).size());
                prepareGenotypeFilesForChr(vcf, giGiType, outputDir, log, imputed, MarkerType.dense, chr, denseMarkerBatchIdx, il, gatkPed);
            }
        }
    }

    public void prepareFrameworkGenotypeFiles(File vcf, GiGiType giGiType, File outputDir, Logger log, List<String> imputed, File gatkPed) throws PipelineJobException
    {
        for (String chr : _frameworkIntervalMap.keySet())
        {
            log.info("processing chromosome: " + chr + ".  for marker set: framework.  to build data of type: " + giGiType.name() + ".  total intervals: " + _frameworkIntervalMap.get(chr).size());

            prepareGenotypeFilesForChr(vcf, giGiType, outputDir, log, imputed, MarkerType.framework, chr, null, _frameworkIntervalMap.get(chr), gatkPed);
        }
    }

    private void prepareGenotypeFilesForChr(File vcf, GiGiType giGiType, File outputDir, Logger log, List<String> imputed, MarkerType markerType, String chr, @Nullable Integer denseMarkerBatchIdx, List<Interval> intervalList, File gatkPed) throws PipelineJobException
    {
        List<List<String>> alleleNameList = markerType == MarkerType.dense ? _denseMarkerAlleles.get(chr).get(denseMarkerBatchIdx) : _frameworkMarkerAlleles.get(chr);
        File subDir = new File(outputDir, chr);
        if (!subDir.exists())
        {
            subDir.mkdirs();
        }

        List<Interval> genotypeBlacklist;
        if (_genotypeBlacklist != null && _genotypeBlacklist.containsKey(chr))
        {
            log.info("total blacklist genotype intervals: " + _genotypeBlacklist.get(chr).size());
            genotypeBlacklist = _genotypeBlacklist.get(chr);
        }
        else
        {
            genotypeBlacklist = Collections.emptyList();
        }

        //write the output to a set of files we wil later merge
        try
        {
            int idx = 1;
            Map<String, File> genoFileMap = new HashMap<>();
            Map<String, BufferedWriter> genoWriterMap = new HashMap<>();

                File vcfIdx = SequenceAnalysisService.get().ensureVcfIndex(vcf, log);
                try (VCFFileReader reader = new VCFFileReader(vcf, vcfIdx, true))
                {
                    try
                    {
                        List<String> sampleNames = reader.getFileHeader().getSampleNamesInOrder();

                        //first check if files exist
                        int existingFiles = 0;
                        for (String name : sampleNames)
                        {
                            if (getGiGiGenotypeFile(markerType, outputDir, chr, name, giGiType, denseMarkerBatchIdx).exists())
                            {
                                existingFiles++;
                            }
                        }

                        if (existingFiles == sampleNames.size())
                        {
                            log.info("all genotype files exist for chr: " + chr + ", will not recreate");
                            return;
                        }

                        for (String name : sampleNames)
                        {
                            File tmp = getGiGiGenotypeFile(markerType, outputDir, chr, name, giGiType, denseMarkerBatchIdx);
                            genoFileMap.put(name, tmp);

                            genoWriterMap.put(name, new BufferedWriter(new FileWriter(tmp)));
                        }

                        INTERVAL: for (Interval i : intervalList)
                        {
                            List<String> knownAlleles = alleleNameList.get(idx - 1);  //idx is 1-based
                            idx++;
                            if (idx % 1000 == 0)
                            {
                                log.info("processed " + idx + " loci");
                            }

                            //if this is a blacklist interval, write zeros for all samples
                            for (Interval other : genotypeBlacklist)
                            {
                                if (other.intersects(i))
                                {
                                    log.info("interval is within blacklist, setting genotypes to zeros: " + other.getStart());
                                    for (String name : sampleNames)
                                    {
                                        //if not found, treat as no call.
                                        BufferedWriter writer = genoWriterMap.get(name);
                                        if (GiGiType.reference == giGiType)
                                        {
                                            writer.append(" ").append("-1");
                                            writer.append(" ").append("-1");
                                        }
                                        else
                                        {
                                            writer.append(" ").append("0");
                                            writer.append(" ").append("0");
                                        }
                                    }

                                    continue INTERVAL;
                                }
                            }

                            try (CloseableIterator<VariantContext> it = reader.query(chr, i.getStart(), i.getEnd()))
                            {
                                String markerName = i.getSequence() + "_" + i.getStart();
                                if (!it.hasNext())
                                {
                                    log.debug("position not found: " + markerName + " in sample: " + vcf.getName());
                                    for (String name : sampleNames)
                                    {
                                        //if not found, treat as no call.  this isnt ideal
                                        BufferedWriter writer = genoWriterMap.get(name);
                                        if (GiGiType.reference == giGiType)
                                        {
                                            writer.append(" ").append("-1");
                                            writer.append(" ").append("-1");
                                        }
                                        else
                                        {
                                            writer.append(" ").append("0");
                                            writer.append(" ").append("0");
                                        }
                                    }
                                }

                                MendelianEvaluator me = new MendelianEvaluator(gatkPed);
                                me.setMinGenotypeQuality(0); //reject all
                                ITERATOR: while (it.hasNext())
                                {
                                    VariantContext ctx = it.next();

                                    if (ctx.getReference().getDisplayString().length() > 1)
                                    {
                                        log.warn("complex reference allele, marking as no call: " + markerName + "/" + ctx.getReference().getDisplayString());
                                        for (String name : sampleNames)
                                        {
                                            BufferedWriter writer = genoWriterMap.get(name);
                                            if (GiGiType.reference == giGiType)
                                            {
                                                writer.append(" ").append("-1");
                                                writer.append(" ").append("-1");
                                            }
                                            else
                                            {
                                                writer.append(" ").append("0");
                                                writer.append(" ").append("0");
                                            }
                                        }

                                        continue ITERATOR;
                                    }

                                    for (String name : ctx.getSampleNames())
                                    {
                                        BufferedWriter writer = genoWriterMap.get(name);
                                        Genotype g = ctx.getGenotype(name);
                                        if (g.getAlleles().size() != 2)
                                        {
                                            log.debug("More than 2 genotypes found for marker: " + ctx.getChr() + " " + ctx.getStart());
                                        }

                                        if (g.isNoCall() || me.isViolation(g.getSampleName(), ctx))
                                        {
                                            if (GiGiType.reference == giGiType)
                                            {
                                                writer.append(" ").append("-1");
                                                writer.append(" ").append("-1");
                                            }
                                            else
                                            {
                                                writer.append(" ").append("0");
                                                writer.append(" ").append("0");
                                            }
                                            continue;
                                        }

                                        if (g.getPhredScaledQual() < _minGenotypeQual)
                                        {
                                            log.debug("low quality genotype (min: " + _minGenotypeQual + "), skipping position: " + (idx - 1) + "/" + ctx.getStart() + ". for sample: " + name + ". qual: " + g.getPhredScaledQual() + "/" + g.getGenotypeString());
                                            if (GiGiType.reference == giGiType)
                                            {
                                                writer.append(" ").append("-1");
                                                writer.append(" ").append("-1");
                                            }
                                            else
                                            {
                                                writer.append(" ").append("0");
                                                writer.append(" ").append("0");
                                            }
                                            continue;
                                        }

                                        if (g.getDP() < _minGenotypeDepth)
                                        {
                                            log.debug("genotype DP below " + _minGenotypeDepth + ", skipping position: " + (idx - 1) + "/" + ctx.getStart() + ". for sample: " + name + ". DP: " + g.getDP() + "/" + g.getGenotypeString());
                                            if (GiGiType.reference == giGiType)
                                            {
                                                writer.append(" ").append("-1");
                                                writer.append(" ").append("-1");
                                            }
                                            else
                                            {
                                                writer.append(" ").append("0");
                                                writer.append(" ").append("0");
                                            }
                                            continue;
                                        }

                                        List<String> toAppend = new ArrayList<>();
                                        for (Allele a : g.getAlleles())
                                        {
                                            if (a.isCalled())
                                            {
                                                if (!knownAlleles.contains(a.getBaseString()))
                                                {
                                                    //TODO: this should throw an exception?
                                                    log.warn("encountered allele in VCF (" + giGiType.name() + ") not found in allele frequency VCF: " + (idx - 1) + "/" + markerName + ", [" + a.getBaseString() + "]. sample: " + name + ". known alleles are: " + StringUtils.join(knownAlleles, ";") + ".  this will be reported as no call");
                                                    toAppend.add("0");
                                                    continue;
                                                }
                                                Integer ai = knownAlleles.indexOf(a.getBaseString()) + 1;

                                                if (ai == 1 && !a.isReference())
                                                {
                                                    throw new PipelineJobException("first allele is non-reference: " + ai + "/" + ctx.getStart() + "/" + a.getBaseString() + "/" + StringUtils.join(knownAlleles, ";"));
                                                }
                                                else if (ai > 2)
                                                {
                                                    log.debug("more than 2 alleles: " + (idx - 1) + "/" + ctx.getStart() + "/" + ai + "/" + a.getBaseString() + "/" + StringUtils.join(knownAlleles, ";"));
                                                }

                                                if (GiGiType.reference == giGiType || _frameworkMarkerNames.contains(markerName) || !imputed.contains(g.getSampleName()))
                                                {
                                                    toAppend.add(a.isReference() ? "1" : ai.toString());
                                                }
                                                else
                                                {
                                                    toAppend.add("0");
                                                }
                                            }
                                            else
                                            {
                                                toAppend.add("0");
                                            }
                                        }

                                        if (toAppend.size() > 2)
                                        {
                                            //todo: not supported right now
                                            log.error("sample: " + name + ", more than 2 alleles at " + markerName + "/" + StringUtils.join(toAppend, ";"));
                                            writer.append(" ").append("0");
                                            writer.append(" ").append("0");
                                        }
                                        else if ((toAppend.contains("0") && new HashSet<>(toAppend).size() > 1))
                                        {
                                            //gl_auto does not support mix of known/unknown genotypes
                                            log.warn("sample: " + name + ", mix of known/unknown genotypes at " + markerName + "/" + StringUtils.join(toAppend, ";"));
                                            if (GiGiType.reference == giGiType)
                                            {
                                                writer.append(" ").append("-1");
                                                writer.append(" ").append("-1");
                                            }
                                            else
                                            {
                                                writer.append(" ").append("0");
                                                writer.append(" ").append("0");
                                            }
                                        }
                                        else
                                        {
                                            writer.append(" ").append(toAppend.get(0));
                                            writer.append(" ").append(toAppend.get(1));
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
                    }
                }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    public void prepareFrameworkResources(File setBaseDir, File rawDataDir, Logger log, List<Pair<Integer, String>> completeGenotypes, List<Pair<Integer, String>> imputed) throws PipelineJobException
    {
        for (String chr : _frameworkIntervalMap.keySet())
        {
            prepareResourcesForChr(setBaseDir, rawDataDir, log, completeGenotypes, imputed, MarkerType.framework, chr, null, _frameworkIntervalMap.get(chr));
        }
    }

    public void prepareDenseResources(File setBaseDir, File rawDataDir, Logger log, List<Pair<Integer, String>> completeGenotypes, List<Pair<Integer, String>> imputed) throws PipelineJobException
    {
        for (String chr : _denseIntervalMapBatched.keySet())
        {
            int denseMarkerIdx = -1;
            for (List<Interval> il : _denseIntervalMapBatched.get(chr))
            {
                denseMarkerIdx++;
                prepareResourcesForChr(setBaseDir, rawDataDir, log, completeGenotypes, imputed, MarkerType.dense, chr, denseMarkerIdx, il);
            }
        }
    }

    private void prepareResourcesForChr(File setBaseDir, File rawDataDir, Logger log, List<Pair<Integer, String>> completeGenotypes, List<Pair<Integer, String>> imputed, MarkerType markerType, String chr, @Nullable Integer denseMarkerBatchIdx, List<Interval> intervalList) throws PipelineJobException
    {
        try
        {
            log.info("preparing resources for GIGI");
            File basedir = new File(setBaseDir, chr);
            if (!basedir.exists())
            {
                basedir.mkdir();
            }

            String separator = denseMarkerBatchIdx == null ? "" : "-" + denseMarkerBatchIdx;
            File markerFile = new File(basedir, "markers" + separator + ".tmp");
            File markerPosFile = new File(basedir, "markerPositions" + separator + ".tmp");
            File mapFile = new File(basedir, markerType.name() + separator + "_map.txt");
            File gigiGenoFile = new File(basedir, markerType.name() + separator + ".gigi.geno");
            File glautoGenoFile = new File(basedir, markerType.name() + separator + ".glauto.geno");

            if (mapFile.exists() && gigiGenoFile.exists() && glautoGenoFile.exists())
            {
                log.info("all resources exist for chr: " + chr + ", re-using");
                return;
            }

            try (
                    BufferedWriter markerLineWriter = new BufferedWriter(new FileWriter(markerFile));
                    BufferedWriter markerPosLineWriter = new BufferedWriter(new FileWriter(markerPosFile));
                    BufferedWriter mapWriter = new BufferedWriter(new FileWriter(mapFile))
            )
            {
                markerLineWriter.write("set marker names");
                markerPosLineWriter.write("map marker positions");

                for (Interval i : intervalList)
                {
                    String markerName = i.getSequence() + "_" + i.getStart();

                    mapWriter.write((i.getStart() / 1000000.0) + "\n");
                    markerLineWriter.append(" ").append(markerName);
                    markerPosLineWriter.append(" " + i.getStart() / 1000000.0);
                }
            }

            File frequencyFile = getAlleleFreqGenotypesFile(rawDataDir, markerType, chr, denseMarkerBatchIdx);

            //now write each version of the marker files for GIGI or GL_AUTO.  These are basically the same file, except the GL_AUTO version includes frequency info
            log.info("writing " + markerType.name() + " genotype files for: " + chr);
            log.debug("using basedir: " + basedir.getPath());

            try (BufferedWriter gigiGenoWriter = new BufferedWriter(new FileWriter(gigiGenoFile));BufferedWriter glautoGenoWriter = new BufferedWriter(new FileWriter(glautoGenoFile)))
            {
                for (File file : Arrays.asList(markerFile, markerPosFile, frequencyFile))
                {
                    try (BufferedReader reader = new BufferedReader(new FileReader(file)))
                    {
                        IOUtils.copy(reader, glautoGenoWriter);
                        glautoGenoWriter.write("\n\n");
                    }

                    //file.delete();
                }

                glautoGenoWriter.write("set marker " + intervalList.size() + " data\n");

                Set<String> distinctImputed = new HashSet<>();
                for (Pair<Integer, String> pair : imputed)
                {
                    File file = getGiGiExperimentalGenotypeFile(markerType, setBaseDir, chr, pair.second, denseMarkerBatchIdx);
                    appendGenotypeLine(file, pair.second, glautoGenoWriter);
                    distinctImputed.add(pair.second);

                    appendGenotypeLine(file, pair.second, gigiGenoWriter);
                }

                for (Pair<Integer, String> pair : completeGenotypes)
                {
                    if (distinctImputed.contains(pair.second))
                    {
                        log.warn("this set already has an imputed sample for: " + pair.second + ", skipping repeat of complete genome data (sampleId: " + pair.first + "|" + pair.second + ")");
                        continue;
                    }

                    File file = getGiGiExperimentalGenotypeFile(markerType, setBaseDir, chr, pair.second, denseMarkerBatchIdx);
                    appendGenotypeLine(file, pair.second, glautoGenoWriter);

                    appendGenotypeLine(file, pair.second, gigiGenoWriter);
                }
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private void appendGenotypeLine(File file, String sampleName, BufferedWriter writer) throws IOException
    {
        try (BufferedReader reader = new BufferedReader(new FileReader(file)))
        {
            writer.write(sampleName + " ");
            IOUtils.copy(reader, writer);
            writer.write("\n");
        }
    }

    public static enum MarkerType
    {
        framework(),
        dense()
    }

    public enum GiGiType
    {
        experimental(),
        reference();
    }

    private File getGiGiGenotypeFile(MarkerType type, File setBaseDir, String chr, String sampleName, GiGiType gigiType, @Nullable Integer denseMarkerBatchIdx)
    {
        setBaseDir = new File(setBaseDir, chr);
        setBaseDir = new File(setBaseDir, "genotypes");
        if (!setBaseDir.exists())
        {
            setBaseDir.mkdirs();
        }
        return new File(setBaseDir, type.name() + "_" + sampleName + "." + gigiType.name() + (denseMarkerBatchIdx == null ? "" : "-" + denseMarkerBatchIdx) + ".geno");
    }

    //this is the file holding the genotypes we expect to give to GIGI.  depending on the sample type, this might be
    private File getGiGiExperimentalGenotypeFile(MarkerType type, File setBaseDir, String chr, String sampleName, Integer denseMarkerBatchIdx)
    {
        return getGiGiGenotypeFile(type, setBaseDir, chr, sampleName, GiGiType.experimental, denseMarkerBatchIdx);
    }

    //this is the file holding reference genotypes available for that sample.  this will not necessarily come from the same VCF as the original data.  often GBS data will have WGS performed in a different experiment/VCF used as the reference
    public File getGiGiReferenceGenotypeFile(MarkerType type, File setBaseDir, String chr, String sampleName, Integer denseMarkerBatchIdx)
    {
        return getGiGiGenotypeFile(type, setBaseDir, chr, sampleName, GiGiType.reference, denseMarkerBatchIdx);
    }

    public Set<String> getDenseChrs()
    {
        return _denseIntervalMapBatched.keySet();
    }

    public List<Interval> getFrameworkIntervals(String chr)
    {
        return _frameworkIntervalMap.get(chr);
    }

    //0-based
    public Interval getDensePositionByIndex(String chr, Integer denseMarkerIndex, int idx)
    {
        return _denseIntervalMapBatched.get(chr).get(denseMarkerIndex).get(idx - 1);
    }

    public int getMinGenotypeQual()
    {
        return _minGenotypeQual;
    }

    public void setMinGenotypeQual(int minGenotypeQual)
    {
        _minGenotypeQual = minGenotypeQual;
    }

    public int getMinGenotypeDepth()
    {
        return _minGenotypeDepth;
    }

    public void setMinGenotypeDepth(int minGenotypeDepth)
    {
        _minGenotypeDepth = minGenotypeDepth;
    }

    public void setDenseMarkerBatchSize(int denseMarkerBatchSize)
    {
        _denseMarkerBatchSize = denseMarkerBatchSize;
    }

    public void doCleanup(Logger log, File baseDir) throws IOException
    {
        for (String chr : _denseIntervalMapBatched.keySet())
        {
            log.info("cleaning up tmp files for chr: " + chr);

            File dir = new File(baseDir, chr);
            File[] files = dir.listFiles();
            if (files != null && files.length > 0)
            {
                for (int i=0;i<files.length;i++)
                {
                    File f = files[i];
                    if (f.getName().endsWith(".tmp"))
                    {
                        f.delete();
                    }
                    else
                    {
                        Compress.compressGzip(f);
                        f.delete();
                    }
                }
            }

            log.info("cleaning up genotypes dir");
            File genotypes = new File(dir, "genotypes");
            File[] genoFiles = genotypes.listFiles();
            if (genoFiles != null && genoFiles.length > 0)
            {
                for (int i=0;i<genoFiles.length;i++)
                {
                    File f = genoFiles[i];
                    if (f.getName().endsWith(".geno"))
                    {
                        f.delete();
                    }
                    else
                    {
                        Compress.compressGzip(f);
                        f.delete();
                    }
                }
            }

            genoFiles = genotypes.listFiles();
            if (genoFiles == null || genoFiles.length == 0)
            {
                FileUtils.deleteDirectory(genotypes);
            }

            log.info("done");
        }
    }
}
