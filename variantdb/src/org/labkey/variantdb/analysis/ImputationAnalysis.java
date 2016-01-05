package org.labkey.variantdb.analysis;

import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.util.Interval;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.run.SelectVariantsWrapper;
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.variantdb.VariantDBModule;
import org.labkey.variantdb.run.CombineVariantsWrapper;
import org.labkey.variantdb.run.ImputationRunner;
import org.labkey.variantdb.run.MendelianEvaluator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Created by bimber on 2/22/2015.
 */
public class ImputationAnalysis implements SequenceOutputHandler
{
    private final FileType _vcfType = new FileType(Arrays.asList("vcf"), "vcf", false, FileType.gzSupportLevel.SUPPORT_GZ);

    public ImputationAnalysis()
    {

    }

    @Override
    public String getName()
    {
        return "GIGIv3 Imputation";
    }

    @Override
    public String getDescription()
    {
        return "This will use GIGI v3 to imputate genotypes given an input VCF file.  It will automatically generate many of the required files for GIGI and MORGAN.  Note: the VCFs must have either been created through LabKey, or be compliant.  This means all sample names must match readsets.  This is necessary because that is how the system looks up SubjectIds and pedigree information.";
    }

    @Override
    public String getButtonJSHandler()
    {
        return null;
    }

    @Override
    public ActionURL getButtonSuccessUrl(Container c, User u, List<Integer> outputFileIds)
    {
        return DetailsURL.fromString("/variantdb/imputationAnalysis.view?outputFileIds=" + StringUtils.join(outputFileIds, ";"), c).getActionURL();
    }

    @Override
    public Module getOwningModule()
    {
        return ModuleLoader.getInstance().getModule(VariantDBModule.class);
    }

    @Override
    public LinkedHashSet<String> getClientDependencies()
    {
        return null;
    }

    @Override
    public List<String> validateParameters(JSONObject params)
    {
        return null;
    }

    @Override
    public boolean canProcess(SequenceOutputFile f)
    {
        return f.getFile() != null && (_vcfType.isType(f.getFile()));
    }

    @Override
    public boolean doRunRemote()
    {
        return true;
    }

    @Override
    public boolean doRunLocal()
    {
        return true;
    }

    @Override
    public OutputProcessor getProcessor()
    {
        return new Processor();
    }

    @Override
    public boolean doSplitJobs()
    {
        return false;
    }

    public class Processor implements OutputProcessor
    {
        private TableInfo _subjectTable = null;

        private TableInfo getSubjectTable(User u, Container c)
        {
            if (_subjectTable == null)
            {
                UserSchema us = QueryService.get().getUserSchema(u, (c.isWorkbook() ? c.getParent() : c), "laboratory");
                _subjectTable = us.getTable("subjects");
            }

            return _subjectTable;
        }

        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            //find genome
            Set<Integer> ids = new HashSet<>();
            for (SequenceOutputFile f : inputFiles)
            {
                ids.add(f.getLibrary_id());

                try
                {
                    SequenceAnalysisService.get().ensureVcfIndex(f.getFile(), job.getLogger());
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            if (ids.size() != 1)
            {
                throw new PipelineJobException("The selected files use more than 1 genome.  All VCFs must use the same genome");
            }

            support.cacheGenome(SequenceAnalysisService.get().getReferenceGenome(ids.iterator().next(), job.getUser()));

            //make ped file
            List<PedigreeRecord> pedigreeRecords = generatePedigree(job, params);

            File gatkPed = new File(job.getJobSupport(FileAnalysisJobSupport.class).getAnalysisDirectory(), "gatkPed.ped");
            File morganPed = new File(job.getJobSupport(FileAnalysisJobSupport.class).getAnalysisDirectory(), "morgan.ped");
            try (BufferedWriter gatkWriter = new BufferedWriter(new FileWriter(gatkPed));BufferedWriter morganWriter = new BufferedWriter(new FileWriter(morganPed)))
            {
                morganWriter.write("input pedigree size " + pedigreeRecords.size() + '\n');
                morganWriter.write("input pedigree record names 3 integers 2\n");
                morganWriter.write("input pedigree record trait 1 integer 2\n"); //necessary to get gl_auto to run
                morganWriter.write("*****" + '\n');
                for (PedigreeRecord pd : pedigreeRecords)
                {
                    List<String> vals = Arrays.asList(pd.subjectName, (StringUtils.isEmpty(pd.father) ? "0" : pd.father), (StringUtils.isEmpty(pd.mother) ? "0" : pd.mother), ("m".equals(pd.gender) ? "1" : "f".equals(pd.gender) ? "2" : "0"), "0");
                    morganWriter.write(StringUtils.join(vals, " ") + '\n');
                    gatkWriter.write("FAM01 " + StringUtils.join(vals, " ") + '\n');
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            ExpData frameworkMarkers = ExperimentService.get().getExpData(params.getInt("frameworkFile"));
            if (frameworkMarkers == null || !frameworkMarkers.getFile().exists())
            {
                throw new PipelineJobException("Unable to find framework markers file: " + params.getInt("frameworkFile"));
            }
            job.getLogger().info("using framework markers file: " + frameworkMarkers.getFile().getPath());
            support.cacheExpData(frameworkMarkers);

            ExpData denseMarkers = ExperimentService.get().getExpData(params.getInt("denseFile"));
            if (denseMarkers == null || !denseMarkers.getFile().exists())
            {
                throw new PipelineJobException("Unable to find dense markers file: " + params.getInt("denseFile"));
            }
            job.getLogger().info("using dense markers file: " + denseMarkers.getFile().getPath());
            support.cacheExpData(denseMarkers);

            ExpData alleleFrequencyFile = ExperimentService.get().getExpData(params.getInt("alleleFrequencyFile"));
            if (alleleFrequencyFile == null || !alleleFrequencyFile.getFile().exists())
            {
                throw new PipelineJobException("Unable to find allele frequency file: " + params.getInt("alleleFrequencyFile"));
            }
            job.getLogger().info("using allele frequency file: " + alleleFrequencyFile.getFile().getPath());
            support.cacheExpData(alleleFrequencyFile);

            if (StringUtils.trimToNull(params.getString("blacklistFile")) != null)
            {
                ExpData blacklist = ExperimentService.get().getExpData(params.getInt("blacklistFile"));
                if (blacklist == null || !blacklist.getFile().exists())
                {
                    throw new PipelineJobException("Unable to find blacklist file: " + params.getInt("blacklistFile"));
                }
                job.getLogger().info("using blacklist: " + blacklist.getFile().getPath());
                support.cacheExpData(blacklist);
            }
        }

        private String potentiallyWriteAliasedName(String subjectId, Map<String, String> aliasMap)
        {
            return aliasMap.containsKey(subjectId) ? aliasMap.get(subjectId) : subjectId;
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            for (Integer i : support.getAllCachedData().keySet())
            {
                job.getLogger().debug("cached data: " + i + "/" + support.getAllCachedData().get(i));
            }

            if (support.getAllCachedData().isEmpty())
            {
                job.getLogger().error("there are no cached ExpDatas");
            }

            File gatkPed = new File(job.getJobSupport(FileAnalysisJobSupport.class).getAnalysisDirectory(), "gatkPed.ped");
            FileType gz = new FileType(".gz");
            List<SampleSet> sets = getSampleSets(params);
            RecordedAction action = new RecordedAction(getName());
            actions.add(action);
            action.addInput(gatkPed, "Pedigree File");
            File denseMarkers = support.getCachedData(params.getInt("denseFile"));
            action.addInput(denseMarkers, "Dense Markers");
            File frameworkMarkers = support.getCachedData(params.getInt("frameworkFile"));
            action.addInput(frameworkMarkers, "Framework Markers");
            File alleleFreqVcf = support.getCachedData(params.getInt("alleleFrequencyFile"));
            action.addInput(alleleFreqVcf, "Allele Frequency File");

            File blackList = null;
            if (StringUtils.trimToNull(params.getString("blacklistFile")) != null)
            {
                blackList = support.getCachedData(params.getInt("blacklistFile"));
                action.addInput(blackList, "Genotype Blacklist");
            }

            //copy locally to retain a record.  we expect these files to be of reasonable size
            File copiedBlackList = null;
            try
            {
                FileUtils.copyFile(denseMarkers, new File(outputDir, denseMarkers.getName()));
                FileUtils.copyFile(frameworkMarkers, new File(outputDir, frameworkMarkers.getName()));

                if (blackList != null)
                {
                    copiedBlackList = new File(job.getJobSupport(FileAnalysisJobSupport.class).getAnalysisDirectory(), "genotypeBlacklist.bed");
                    FileUtils.copyFile(blackList, copiedBlackList);
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            if (copiedBlackList != null)
            {
                job.getLogger().info("genotype blacklist found, using: " + copiedBlackList.getName());
            }

            ImputationRunner runner = new ImputationRunner(denseMarkers, frameworkMarkers, copiedBlackList, job.getLogger());
            File rawDataDir = new File(job.getJobSupport(FileAnalysisJobSupport.class).getAnalysisDirectory(), "rawData");
            rawDataDir.mkdirs();

            if (params.get("minGenotypeQual") != null)
            {
                job.getLogger().info("setting minGenotypeQual: " + params.get("minGenotypeQual"));
                runner.setMinGenotypeQual(params.getInt("minGenotypeQual"));
            }

            if (params.get("minGenotypeDepth") != null)
            {
                job.getLogger().info("setting minGenotypeDepth: " + params.get("minGenotypeDepth"));
                runner.setMinGenotypeDepth(params.getInt("minGenotypeDepth"));
            }

            //write allele frequency data and make map of marker/NT
            runner.prepareFrequencyFiles(alleleFreqVcf, rawDataDir, job.getLogger());

            File summary = new File(job.getJobSupport(FileAnalysisJobSupport.class).getAnalysisDirectory(), "summary.txt");
            action.addOutput(summary, "Summary Table", false);

            File errorSummary = new File(job.getJobSupport(FileAnalysisJobSupport.class).getAnalysisDirectory(), "errorSummary.txt");
            try (CSVWriter writer = new CSVWriter(new FileWriter(summary), '\t', CSVWriter.NO_QUOTE_CHARACTER);CSVWriter errorWriter = new CSVWriter(new FileWriter(errorSummary), '\t', CSVWriter.NO_QUOTE_CHARACTER))
            {
                writer.writeNext(new String[]{"SetName", "TotalFramework", "TotalDense", "CompleteGenotypes", "ImputedSubjects", "Subject", "CallMethod", "MinGenotypeQual", "MinGenotypeDepth", "Chr", "TotalMarkers", "TotalMatching", "TotalErrors", "TotalMissing", "TotalNonCalledRef", "TotalIncorrectNonCalledRef", "PctMatching", "PctMissing", "PctMatchingWithoutNonCalled", "PctMissingWithoutNonCalled", "NumFirstOrderRelativesWithWGS", "NumFirstOrderRelativesPresent", "FirstOrderRelativesWithWGS", "FirstOrderRelativesPresent", "TotalImputed", "TotalLowFreqHetMatching", "TotalLowFreqHetErrors"});
                errorWriter.writeNext(new String[]{"SetName", "CompleteGenotypes", "ImputedSubjects", "TotalImputed", "Subject", "MarkerNumber", "Chr", "Position", "ImputedGenotype", "ActualGenotype", "FirstOrderRelativesWithWGS", "FirstOrderRelativesPresent"});

                Integer idx = 0;
                for (SampleSet ss : sets)
                {
                    idx++;
                    job.getLogger().info("Starting set " + idx + " of " + sets.size());
                    job.setStatus("Set " + idx + " of " + sets.size());

                    File baseDir = new File(job.getJobSupport(FileAnalysisJobSupport.class).getAnalysisDirectory(), "Set-" + idx.toString());
                    if (!baseDir.exists())
                    {
                        baseDir.mkdirs();
                    }

                    buildCombinedVcf(runner, ss, inputFiles, support, job.getLogger(), baseDir, params, action, gatkPed);

                    runner.prepareFrameworkResources(baseDir, rawDataDir, job.getLogger(), ss.wgsSampleIds, ss.imputedSampleIds);
                    runner.prepareDenseResources(baseDir, rawDataDir, job.getLogger(), ss.wgsSampleIds, ss.imputedSampleIds);

                    Map<String, PedigreeRecord> pedigreeRecordMap = parsePedigree(gatkPed);
                    job.getLogger().debug("pedigree size: " + pedigreeRecordMap.size());

                    //now actually perform imputation
                    String callMethod = params.get("callMethod") != null ? params.getString("callMethod") : "1";
                    runner.processSet(baseDir, rawDataDir, job.getLogger(), ss.wgsSampleIds, ss.imputedSampleIds, callMethod);

                    if (StringUtils.trimToNull(params.getString("denseMarkerBatchSize")) != null)
                    {
                        job.getLogger().info("using batch size of: " + params.getInt("denseMarkerBatchSize"));
                        runner.setDenseMarkerBatchSize(params.getInt("denseMarkerBatchSize"));
                    }

                    //calculate relatives present in WGS per subject:
                    Map<String, Set<String>> relativesPresent = new TreeMap<>();
                    Map<String, Set<String>> wgsRelativesPresent = new TreeMap<>();
                    Set<PedigreeRecord> wgsSubjects = new HashSet<>();
                    Set<PedigreeRecord> allSubjects = new HashSet<>();
                    for (Pair<Integer, String> id : ss.wgsSampleIds)
                    {
                        wgsSubjects.add(pedigreeRecordMap.get(id.second));
                        allSubjects.add(pedigreeRecordMap.get(id.second));
                    }

                    for (Pair<Integer, String> id : ss.imputedSampleIds)
                    {
                        allSubjects.add(pedigreeRecordMap.get(id.second));
                    }

                    for (Pair<Integer, String> imputedSubj : ss.imputedSampleIds)
                    {
                        PedigreeRecord pr = pedigreeRecordMap.get(imputedSubj.second);
                        if (pr == null)
                        {
                            throw new PipelineJobException("unable to find pedigree record for id: [" + imputedSubj.second + "]");
                        }

                        relativesPresent.put(imputedSubj.second, pr.getRelativesPresent(allSubjects));
                        wgsRelativesPresent.put(imputedSubj.second, pr.getRelativesPresent(wgsSubjects));
                    }

                    final double lowFreqThreshold = 0.05;

                    for (String chr : runner.getDenseChrs())
                    {
                        job.getLogger().info("calculating accuracy for chromosome: " + chr);
                        Set<Integer> distinctLowAfMarkers = new HashSet<>();

                        int denseIntervalIdx = -1;
                        Map<String, SubjectCounter> counterMap = new HashMap<>();

                        for (List<Interval> denseIntervalList : runner.getDenseIntervalMapBatched().get(chr))
                        {
                            denseIntervalIdx++;
                            job.getLogger().info("processing dense marker batch " + denseIntervalIdx + " of " + runner.getDenseIntervalMapBatched().get(chr).size());

                            File imputed = new File(baseDir, chr + "/impute-" + denseIntervalIdx + ".geno");
                            if (!imputed.exists())
                            {
                                job.getLogger().error("Unable to find imputed genotypes for batch: " + denseIntervalIdx + ", skipping");
                                job.getLogger().error("start: " + runner.getDenseIntervalMapBatched().get(chr).get(denseIntervalIdx).get(0).getStart());
                                job.getLogger().error("end: " + runner.getDenseIntervalMapBatched().get(chr).get(denseIntervalIdx).get(runner.getDenseIntervalMapBatched().get(chr).get(denseIntervalIdx).size() - 1).getStart());
                                continue;
                            }

                            //gather allele freqs
                            File freqs = runner.getAlleleFreqFile(rawDataDir, ImputationRunner.MarkerType.dense, chr, denseIntervalIdx);
                            if (!freqs.exists())
                            {
                                throw new PipelineJobException("Unable to find frequency file: " + freqs.getPath());
                            }

                            List<List<Double>> alleleFreqs = new ArrayList<>();
                            try (BufferedReader freqReader = new BufferedReader(new FileReader(freqs)))
                            {
                                String line;
                                while ((line = freqReader.readLine()) != null)
                                {
                                    String[] split = line.split(" ");
                                    List<Double> list = new ArrayList<>();
                                    for (String token : split)
                                    {
                                        list.add(ConvertHelper.convert(token, Double.class));
                                    }

                                    alleleFreqs.add(list);
                                }
                            }

                            try (BufferedReader imputedReader = new BufferedReader(new FileReader(imputed)))
                            {
                                String imputedLine;
                                OUTER: while ((imputedLine = imputedReader.readLine()) != null)
                                {
                                    String[] imputedData = imputedLine.split("( )+");
                                    if (!ss.imputedSampleIdStrings.contains(imputedData[0]))
                                    {
                                        job.getLogger().info("skipping non-imputed subject: " + imputedData[0]);
                                        continue OUTER;
                                    }

                                    String[] trueGenotypesData = null;

                                    Integer fileId = ss.sampleFileMap.get(imputedData[0]);
                                    if (fileId == null)
                                    {
                                        throw new PipelineJobException("unable to find file matching sample: " + imputedData[0]);
                                    }

                                    Pair<Integer, String> refPair = ss.getReferenceForImputedSample(imputedData[0]);
                                    if (refPair == null)
                                    {
                                        job.getLogger().info("no reference available for sample: " + imputedData[0] + ", skipping");
                                        continue;
                                    }

                                    if (!refPair.first.equals(fileId))
                                    {
                                        job.getLogger().info("using alternate reference data for sample: " + imputedData[0]);
                                    }

                                    File trueGenotypes = runner.getGiGiReferenceGenotypeFile(ImputationRunner.MarkerType.dense, baseDir, chr, imputedData[0], denseIntervalIdx);
                                    if (trueGenotypes == null || !trueGenotypes.exists())
                                    {
                                        job.getLogger().warn("unable to find reference genotype file, skipping: " + imputedData[0]);
                                        continue;
                                    }

                                    try (BufferedReader trueGenotypereader = new BufferedReader(new FileReader(trueGenotypes)))
                                    {
                                        String trueGenotypesLine;
                                        INNER: while ((trueGenotypesLine = trueGenotypereader.readLine()) != null)
                                        {
                                            String[] lineData = trueGenotypesLine.split("( )+");
                                            trueGenotypesData = lineData;
                                            break INNER;
                                        }
                                    }

                                    if (!ss.imputedSampleIdStrings.contains(imputedData[0]))
                                    {
                                        job.getLogger().info("skipping non-imputed subject: " + imputedData[0]);
                                        continue OUTER;
                                    }

                                    if (trueGenotypesData == null)
                                    {
                                        //throw new PipelineJobException("Unable to find reference genotypes for subject: " + imputedData[0]);
                                        //NOTE: GIGI will infer genotypes for parents and other subjects not in our set
                                        job.getLogger().warn("Unable to find reference genotypes for subject: " + imputedData[0]);
                                        continue OUTER;
                                    }

                                    if (trueGenotypesData.length != imputedData.length)
                                    {
                                        job.getLogger().error("reference and imputed genotypes files do not have the same number of markers, skipping: " + trueGenotypesData.length + ", " + imputedData.length + ", " + imputed.getPath());
                                        continue;
                                    }

                                    job.getLogger().info("validating imputed subject: " + imputedData[0]);

                                    if (!counterMap.containsKey(imputedData[0]))
                                    {
                                        counterMap.put(imputedData[0], new SubjectCounter());
                                    }

                                    SubjectCounter counter = counterMap.get(imputedData[0]);
                                    for (int i = 1; i < trueGenotypesData.length; i++)
                                    {
                                        counter.totalMarkers++;
                                        counter.totalMarkers++; //we are cycling through 2 positions
                                        List<String> imputedGenos = new ArrayList<>(Arrays.asList(imputedData[i], imputedData[i + 1]));
                                        Collections.sort(imputedGenos);
                                        List<String> trueGenos = new ArrayList<>(Arrays.asList(trueGenotypesData[i], trueGenotypesData[i + 1]));
                                        Collections.sort(trueGenos);

                                        i++; //additional increment for 2nd marker

                                        int markerNumber = i / 2;  //there are 2 markers per genome position.  this is 1-based.  this is marker#

                                        //no value imputed
                                        if (imputedGenos.get(0).equals("0"))
                                        {
                                            counter.totalMissing++;
                                            if (trueGenos.get(0).equals("-1"))
                                            {
                                                counter.nonCalledRef++;
                                            }
                                        }
                                        //match
                                        else if (trueGenos.get(0).equals(imputedGenos.get(0)))
                                        {
                                            counter.totalMatching++;

                                            Integer geno = Integer.parseInt(imputedGenos.get(0));
                                            Double maf = geno > 0 ? alleleFreqs.get(markerNumber - 1).get(geno - 1) : null;
                                            if (maf != null && maf <= lowFreqThreshold && !imputedGenos.get(0).equals(imputedGenos.get(1)))
                                            {
                                                counter.totalLowFreqHetMatching++;
                                                distinctLowAfMarkers.add(markerNumber);
                                            }
                                        }
                                        //reference is no call
                                        else if (trueGenos.get(0).equals("-1"))
                                        {
                                            counter.nonCalledRef++;
                                            counter.incorrectNonCalledRef++;
                                        }
                                        else
                                        {
                                            counter.totalErrors++;
                                            Integer geno = Integer.parseInt(imputedGenos.get(0));
                                            Double maf = geno > 0 ? alleleFreqs.get(markerNumber - 1).get(geno - 1) : null;
                                            if (maf != null && maf <= lowFreqThreshold && !imputedGenos.get(0).equals(imputedGenos.get(1)))
                                            {
                                                counter.totalLowFreqHetErrors++;
                                                distinctLowAfMarkers.add(markerNumber);
                                            }
                                        }

                                        //second geno
                                        if (imputedGenos.get(1).equals("0"))
                                        {
                                            counter.totalMissing++;
                                            if (trueGenos.get(1).equals("-1"))
                                            {
                                                counter.nonCalledRef++;
                                            }
                                        }
                                        else if (trueGenos.get(1).equals(imputedGenos.get(1)))
                                        {
                                            counter.totalMatching++;
                                            Integer geno = Integer.parseInt(imputedGenos.get(1));
                                            Double maf = geno > 0 ? alleleFreqs.get(markerNumber - 1).get(geno - 1) : null;
                                            if (maf != null && maf <= lowFreqThreshold && !imputedGenos.get(0).equals(imputedGenos.get(1)))
                                            {
                                                counter.totalLowFreqHetMatching++;
                                                distinctLowAfMarkers.add(markerNumber);
                                            }
                                        }
                                        else if (trueGenos.get(1).equals("-1"))
                                        {
                                            counter.nonCalledRef++;
                                            counter.incorrectNonCalledRef++;
                                        }
                                        else
                                        {
                                            counter.totalErrors++;
                                            Integer geno = Integer.parseInt(imputedGenos.get(1));
                                            Double maf = geno > 0 ? alleleFreqs.get(markerNumber - 1).get(geno - 1) : null;
                                            if (maf != null && maf <= lowFreqThreshold && !imputedGenos.get(0).equals(imputedGenos.get(1)))
                                            {
                                                counter.totalLowFreqHetErrors++;
                                                distinctLowAfMarkers.add(markerNumber);
                                            }
                                        }

                                        if ((!trueGenos.get(0).equals(imputedGenos.get(0)) || !trueGenos.get(1).equals(imputedGenos.get(1))) || (!imputedGenos.get(0).equals("0") || !imputedGenos.get(1).equals("0")))
                                        {
                                            Interval errorIv = runner.getDensePositionByIndex(chr, denseIntervalIdx, markerNumber);
                                            if (errorIv == null)
                                            {
                                                job.getLogger().error("Unable to find dense interval for idx: " + i);
                                            }
                                            else
                                            {
                                                errorWriter.writeNext(new String[]{
                                                        idx.toString(),
                                                        StringUtils.join(ss.wgsSampleIdStrings, ";"),
                                                        StringUtils.join(ss.imputedSampleIdStrings, ";"),
                                                        String.valueOf(ss.imputedSampleIdStrings.size()),
                                                        imputedData[0],
                                                        String.valueOf(markerNumber),
                                                        chr,
                                                        String.valueOf(errorIv.getStart()),
                                                        StringUtils.join(imputedGenos, ";"),
                                                        StringUtils.join(trueGenos, ";"),
                                                        String.valueOf(wgsRelativesPresent.get(imputedData[0]).size()),
                                                        String.valueOf(relativesPresent.get(imputedData[0]).size())
                                                });
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        for (String subject : counterMap.keySet())
                        {
                            SubjectCounter counter = counterMap.get(subject);
                            writer.writeNext(new String[]{
                                    idx.toString(),
                                    String.valueOf(runner.getFrameworkIntervalMap().get(chr).size()),
                                    String.valueOf(runner.getDenseIntervalMap().get(chr).size()),
                                    StringUtils.join(ss.wgsSampleIdStrings, ";"),
                                    StringUtils.join(ss.imputedSampleIdStrings, ";"),
                                    subject,
                                    callMethod,
                                    String.valueOf(runner.getMinGenotypeQual()),
                                    String.valueOf(runner.getMinGenotypeDepth()),
                                    chr,
                                    String.valueOf(counter.totalMarkers),
                                    String.valueOf(counter.totalMatching),
                                    String.valueOf(counter.totalErrors),
                                    String.valueOf(counter.totalMissing),
                                    String.valueOf(counter.nonCalledRef),
                                    String.valueOf(counter.incorrectNonCalledRef),
                                    String.valueOf((double) counter.totalMatching / (counter.totalMarkers - counter.totalMissing)),
                                    String.valueOf((double) counter.totalMissing / counter.totalMarkers),
                                    String.valueOf((double) counter.totalMatching / (counter.totalMarkers - counter.totalMissing - counter.incorrectNonCalledRef)),
                                    String.valueOf((double) counter.totalMissing / (counter.totalMarkers - counter.incorrectNonCalledRef)),
                                    String.valueOf(wgsRelativesPresent.get(subject).size()),
                                    String.valueOf(relativesPresent.get(subject).size()),
                                    StringUtils.join(wgsRelativesPresent.get(subject), ";"),
                                    StringUtils.join(relativesPresent.get(subject), ";"),
                                    String.valueOf(ss.imputedSampleIds.size()),
                                    String.valueOf(counter.totalLowFreqHetMatching),
                                    String.valueOf(counter.totalLowFreqHetErrors)
                            });
                        }

                        job.getLogger().info("distinct low AF het markers with data for " + chr + ": " + distinctLowAfMarkers.size());
                    }

                    runner.doCleanup(job.getLogger(), baseDir);
                }

                job.getLogger().info("cleaning up rawData dir");
                File[] rawData = rawDataDir.listFiles();
                if (rawData != null && rawData.length > 0)
                {
                    for (int i=0;i<rawData.length;i++)
                    {
                        File f = rawData[i];
                        Compress.compressGzip(f);
                        f.delete();
                    }
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            Compress.compressGzip(errorSummary);
            errorSummary.delete();

            action.addOutput(new File(errorSummary.getPath() + ".gz"), "Error Summary Table", false);
        }

        private class SubjectCounter
        {
            int totalMarkers = 0;
            int totalMatching = 0;
            int totalErrors = 0;
            int totalMissing = 0;
            int nonCalledRef = 0;
            int incorrectNonCalledRef = 0;

            int totalLowFreqHetMatching = 0;
            int totalLowFreqHetErrors = 0;
        }

        private File buildCombinedVcf(ImputationRunner runner, SampleSet ss, List<SequenceOutputFile> inputFiles, SequenceAnalysisJobSupport support, Logger log, File setBaseDir, JSONObject params, RecordedAction action, File gatkPed) throws PipelineJobException
        {
            File genotypeDir = new File(setBaseDir, "genotypes");
            if (!genotypeDir.exists())
            {
                genotypeDir.mkdirs();
            }

            Set<File> toDelete = new HashSet<>();

            Set<Integer> genomeIds = new HashSet<>();
            for (SequenceOutputFile o : inputFiles)
            {
                genomeIds.add(o.getLibrary_id());
            }
            ReferenceGenome genome = support.getCachedGenome(genomeIds.iterator().next());

            //this essentially allows resume mid-job
            File mergedVcf = new File(genotypeDir, "merged.vcf.gz");
            File refMergedVcf = new File(genotypeDir, "ref_merged.vcf.gz");
            if (!mergedVcf.exists())
            {
                //subset input VCFs taking only samples needed per
                Map<Integer, Set<String>> samplesNeeded = new HashMap<>();
                Map<Integer, Set<String>> refSamplesNeeded = new HashMap<>();
                for (Pair<Integer, String> pair : ss.wgsSampleIds)
                {
                    Set<String> samples = samplesNeeded.get(pair.first);
                    if (samples == null)
                    {
                        samples = new HashSet<>();
                    }

                    samples.add(pair.second);
                    samplesNeeded.put(pair.first, samples);
                }

                for (Pair<Integer, String> pair : ss.imputedSampleIds)
                {
                    Set<String> samples = samplesNeeded.get(pair.first);
                    if (samples == null)
                    {
                        samples = new HashSet<>();
                    }

                    samples.add(pair.second);
                    samplesNeeded.put(pair.first, samples);
                }

                for (Pair<Integer, String> pair : ss.referenceSamples)
                {
                    Set<String> samples = refSamplesNeeded.get(pair.first);
                    if (samples == null)
                    {
                        samples = new HashSet<>();
                    }

                    samples.add(pair.second);
                    refSamplesNeeded.put(pair.first, samples);
                }

                createMergedVcfForSamples(samplesNeeded, genome, inputFiles, support, genotypeDir, toDelete, params, log, mergedVcf, null);
                if (!refSamplesNeeded.isEmpty())
                    createMergedVcfForSamples(refSamplesNeeded, genome, inputFiles, support, genotypeDir, toDelete, params, log, refMergedVcf, "ref");
            }
            else
            {
                log.info("reusing existing file: " + mergedVcf.getName());
            }

            //then perform mendelian check
            File ret;
            if (params.optBoolean("skipMendelianCheck", false))
            {
                ret = mergedVcf;
            }
            else
            {
                try
                {
                    MendelianEvaluator me = new MendelianEvaluator(gatkPed);
                    File mendelianPass = new File(mergedVcf.getParentFile(), "merged.mendelianPass.vcf.gz");
                    File nonMendelianVcf = new File(mergedVcf.getParentFile(), "merged.mendelianViolations.vcf.gz");

                    //essentially allows resume on failed jobs
                    if (!mendelianPass.exists())
                    {
                        log.info("identifying non-mendelian SNPs");
                        me.checkVcf(mergedVcf, mendelianPass, nonMendelianVcf, log);
                    }
                    else
                    {
                        log.info("reusing existing file: " + mendelianPass.getName());
                    }

                    ret = mendelianPass;
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            prepareGenotypeData(runner, ss, setBaseDir, log, ret, refMergedVcf, gatkPed);

//            for (File vcf : toDelete)
//            {
//                if (vcf != null)
//                {
//                    File index = new File(vcf.getPath() + ".idx");
//                    if (index.exists())
//                    {
//                        index.delete();
//                    }
//
//                    index = new File(vcf.getPath() + ".tbi");
//                    if (index.exists())
//                    {
//                        index.delete();
//                    }
//
//                    log.info("deleting temporary file: " + vcf.getPath());
//                    vcf.delete();
//                }
//            }

            return ret;
        }

        private void createMergedVcfForSamples(Map<Integer, Set<String>> samplesNeeded, ReferenceGenome genome, List<SequenceOutputFile> inputFiles, SequenceAnalysisJobSupport support, File outputDir, Set<File> toDelete, JSONObject params, Logger log, File mergedVcf, String suffix) throws PipelineJobException
        {
            if (mergedVcf.exists())
            {
                log.info("using existing merged VCF: " + mergedVcf.getPath());
                return;
            }

            List<File> subsetVcfs = new ArrayList<>();
            log.info("creating merged vcf: " + (suffix == null ? "" : suffix));
            for (Integer i : samplesNeeded.keySet())
            {
                log.info("\tfile id: " + i);
                for (String sample : samplesNeeded.get(i))
                {
                    log.info("\t\tsample name: " + sample);
                }
            }

            if (samplesNeeded.isEmpty())
            {
                log.warn("no samples found, cannot make merged vcf: " + suffix);
                return;
            }

            for (Integer rowId : samplesNeeded.keySet())
            {
                File inputVcf = null;
                for (SequenceOutputFile o : inputFiles)
                {
                    if (o.getRowid().equals(rowId))
                    {
                        inputVcf = o.getFile();
                        break;
                    }
                }

                if (inputVcf == null)
                {
                    throw new PipelineJobException("unable to find output file: " + rowId);
                }

                SelectVariantsWrapper sv = new SelectVariantsWrapper(log);
                File output = new File(outputDir, rowId + ".subset" + (suffix == null ? "" : "." + suffix) + ".vcf.gz");
                subsetVcfs.add(output);
                //todo
                //toDelete.add(output);
                List<String> args = new ArrayList<>();
                File denseMarkers = support.getCachedData(params.getInt("denseFile"));
                File frameworkMarkers = support.getCachedData(params.getInt("frameworkFile"));
                args.add("-L");
                args.add(denseMarkers.getPath());
                args.add("-L");
                args.add(frameworkMarkers.getPath());

                args.add("--selectTypeToExclude");
                args.add("INDEL");
                args.add("-trimAlternates");

                for (String sn : samplesNeeded.get(rowId))
                {
                    args.add("-sn");
                    args.add(sn);
                }
                sv.execute(genome.getWorkingFastaFile(), inputVcf, output, args);
            }

            //then merge
            List<String> args = new ArrayList<>();
            //args.add("-genotypeMergeOptions");
            //args.add("UNIQUIFY");

            CombineVariantsWrapper wrapper = new CombineVariantsWrapper(log);
            wrapper.execute(genome.getWorkingFastaFile(), subsetVcfs, mergedVcf, args);
        }

        private void prepareGenotypeData(ImputationRunner runner, SampleSet ss, File setBaseDir, Logger log, File mergedVcf, File refVcf, File gatkPed) throws PipelineJobException
        {
            //create any resources needed per sample:
            List<String> wgs = new ArrayList<>();
            List<String> imputed = new ArrayList<>();
            for (Pair<Integer, String> pair : ss.wgsSampleIds)
            {
                wgs.add(pair.second);
            }

            for (Pair<Integer, String> pair : ss.imputedSampleIds)
            {
                imputed.add(pair.second);
            }

            //write individual genotype data
            runner.prepareDenseGenotypeFiles(mergedVcf, ImputationRunner.GiGiType.experimental, setBaseDir, log, imputed, gatkPed);
            runner.prepareFrameworkGenotypeFiles(mergedVcf, ImputationRunner.GiGiType.experimental, setBaseDir, log, imputed, gatkPed);

            if (refVcf != null && refVcf.exists())
            {
                runner.prepareDenseGenotypeFiles(refVcf, ImputationRunner.GiGiType.reference, setBaseDir, log, Collections.<String>emptyList(), gatkPed);
                runner.prepareFrameworkGenotypeFiles(refVcf, ImputationRunner.GiGiType.reference, setBaseDir, log, Collections.<String>emptyList(), gatkPed);
            }
        }

        private class SampleSet
        {
            List<String> wgsSampleIdStrings;
            List<String> imputedSampleIdStrings;
            Map<String, Integer> sampleFileMap;
            List<Pair<Integer, String>> wgsSampleIds;
            List<Pair<Integer, String>> imputedSampleIds;
            List<Pair<Integer, String>> referenceSamples;

            public SampleSet(JSONArray arr)
            {
                sampleFileMap = new HashMap<>();
                wgsSampleIds = new ArrayList<>();
                wgsSampleIdStrings = new ArrayList<>();
                JSONArray completeSet = arr.getJSONArray(0);
                for (int j=0;j<completeSet.length();j++)
                {
                    String[] s = completeSet.getString(j).split("\\|\\|");
                    wgsSampleIds.add(Pair.of(Integer.parseInt(s[0]), s[1]));
                    wgsSampleIdStrings.add(s[1]);
                    sampleFileMap.put(s[1], Integer.parseInt(s[0]));
                }

                referenceSamples = new ArrayList<>();
                imputedSampleIds = new ArrayList<>();
                imputedSampleIdStrings = new ArrayList<>();
                JSONArray imputeSet = arr.getJSONArray(1);

                for (int j=0;j<imputeSet.length();j++)
                {
                    String[] s = imputeSet.getString(j).split("\\|\\|");
                    imputedSampleIds.add(Pair.of(Integer.parseInt(s[0]), s[1]));
                    imputedSampleIdStrings.add(s[1]);
                    sampleFileMap.put(s[1], Integer.parseInt(s[0]));
                }

                JSONArray refSet = arr.length() > 2 ? arr.getJSONArray(2) : null;
                if (refSet != null)
                {
                    for (int j=0;j<refSet.length();j++)
                    {
                        String[] i = refSet.getString(j).split("\\|\\|");
                        referenceSamples.add(Pair.of(Integer.parseInt(i[0]), i[1]));
                    }
                }

                wgsSampleIdStrings.removeAll(imputedSampleIdStrings);
            }

            public Pair<Integer, String> getReferenceForImputedSample(String sampleName) throws PipelineJobException
            {
                for (Pair<Integer, String> p : referenceSamples)
                {
                    if (p.second.equals(sampleName))
                    {
                        return p;
                    }
                }

                for (Pair<Integer, String> p : wgsSampleIds)
                {
                    if (p.second.equals(sampleName))
                    {
                        return p;
                    }
                }

                return null;
            }
        }

        private List<SampleSet> getSampleSets(JSONObject params)
        {
            List<SampleSet> ret = new ArrayList<>();

            if (params.containsKey("sampleSets"))
            {
                for (Object o : params.getJSONArray("sampleSets").toArray())
                {
                    ret.add(new SampleSet((JSONArray) o));
                }
            }

            return ret;
        }

        private Map<String, PedigreeRecord> parsePedigree(File ped) throws PipelineJobException
        {
            try (BufferedReader reader = new BufferedReader(new FileReader(ped)))
            {
                Map<String, PedigreeRecord> ret = new HashMap<>();
                String line;
                while ((line = reader.readLine()) != null)
                {
                    String[] tokens = line.split(" ");
                    if (tokens.length < 2)
                    {
                        continue;
                    }

                    PedigreeRecord r = new PedigreeRecord();
                    r.subjectName = tokens[1];
                    r.father = "0".equals(tokens[2]) ? null : tokens[2];
                    r.mother = "0".equals(tokens[3]) ? null : tokens[3];

                    ret.put(r.subjectName, r);
                }

                return ret;
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }

    public static class PedigreeRecord
    {
        String subjectName;
        String father;
        String mother;
        String gender;

        public PedigreeRecord()
        {

        }

        /**
         * returns the first order relatives present in the passed list.
         */
        public Set<String> getRelativesPresent(Collection<PedigreeRecord> animals)
        {
            Set<String> ret = new HashSet<>();
            for (PedigreeRecord potentialRelative : animals)
            {
                if (isParentOf(potentialRelative) || isChildOf(potentialRelative))
                {
                    ret.add(potentialRelative.subjectName);
                }
            }

            return ret;
        }

        public boolean isParentOf(PedigreeRecord potentialOffspring)
        {
            return subjectName.equals(potentialOffspring.father) || subjectName.equals(potentialOffspring.mother);
        }

        public boolean isChildOf(PedigreeRecord potentialParent)
        {
            if (father != null && father.equals(potentialParent.subjectName))
            {
                return true;
            }
            else if (mother != null && mother.equals(potentialParent.subjectName))
            {
                return true;
            }

            return false;
        }

        public String getSubjectName()
        {
            return subjectName;
        }

        public void setSubjectName(String subjectName)
        {
            this.subjectName = subjectName;
        }

        public String getFather()
        {
            return father;
        }

        public void setFather(String father)
        {
            this.father = father;
        }

        public String getMother()
        {
            return mother;
        }

        public void setMother(String mother)
        {
            this.mother = mother;
        }

        public String getGender()
        {
            return gender;
        }

        public void setGender(String gender)
        {
            this.gender = gender;
        }
    }

    public static List<PedigreeRecord> generatePedigree(PipelineJob job, JSONObject params)
    {
        Set<String> sampleNames = new HashSet<>();
        Map<String, String> subjectToReadsetNameMap = new HashMap<>();
        TableInfo subjectTable = QueryService.get().getUserSchema(job.getUser(), (job.getContainer().isWorkbook() ? job.getContainer().getParent() : job.getContainer()), "laboratory").getTable("subjects");
        TableInfo readsetTable = QueryService.get().getUserSchema(job.getUser(), job.getContainer(), "sequenceanalysis").getTable("sequence_readsets");
        if (params.containsKey("sampleSets"))
        {
            for (Object o : params.getJSONArray("sampleSets").toArray())
            {
                JSONArray arr = (JSONArray)o;
                for (int i=0;i<arr.length();i++)
                {
                    JSONArray set = arr.getJSONArray(i);
                    for (int j=0;j<set.length();j++)
                    {
                        //try to find this in the subjects table
                        String[] tokens = set.getString(j).split("\\|\\|");
                        if (new TableSelector(subjectTable, new SimpleFilter(FieldKey.fromString("subjectname"), tokens[1]), null).exists())
                        {
                            sampleNames.add(tokens[1]);
                        }
                        else
                        {
                            //if not, see if it matches a readset and resolve subject from there
                            TableSelector ts = new TableSelector(readsetTable, PageFlowUtil.set("subjectId"), new SimpleFilter(FieldKey.fromString("name"), tokens[1]), null);
                            String subjectId = ts.getObject(String.class);
                            if (subjectId != null)
                            {
                                job.getLogger().info("resolving readset: " + set.getString(0) + " to subject: " + subjectId);
                                sampleNames.add(subjectId);
                                if (subjectToReadsetNameMap.containsKey(subjectId) && !subjectToReadsetNameMap.get(subjectId).equals(set.getString(0)))
                                {
                                    job.getLogger().error("more than one readset present using the same subject ID.  this will cause an inaccurate or incomplete pedigree.");
                                }

                                subjectToReadsetNameMap.put(subjectId, set.getString(0));
                            }
                        }
                    }
                }
            }
        }

        final List<PedigreeRecord> pedigreeRecords = new ArrayList<>();
        TableSelector ts = new TableSelector(subjectTable, PageFlowUtil.set("subjectname", "mother", "father", "gender"), new SimpleFilter(FieldKey.fromString("subjectname"), sampleNames, CompareType.IN), null);
        ts.forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                PedigreeRecord pedigree = new PedigreeRecord();
                pedigree.subjectName = rs.getString("subjectname");
                pedigree.father = rs.getString("father");
                pedigree.mother = rs.getString("mother");
                pedigree.gender = rs.getString("gender");
                if (!StringUtils.isEmpty(pedigree.subjectName))
                    pedigreeRecords.add(pedigree);
            }
        });

        //insert record for any missing parents:
        Set<String> subjects = new HashSet<>();
        for (PedigreeRecord p : pedigreeRecords)
        {
            subjects.add(p.subjectName);
        }

        job.getLogger().info("initial subjects: " + pedigreeRecords.size());
        if (params.optBoolean("includeAncestors", false))
        {
            job.getLogger().info("will attempt to include all ancestors");
        }

        Set<String> distinctSubjects = new HashSet<>();
        for (PedigreeRecord p : pedigreeRecords)
        {
            distinctSubjects.add(p.subjectName);
        }

        List<PedigreeRecord> newRecords = new ArrayList<>();
        for (PedigreeRecord p : pedigreeRecords)
        {
            appendParents(job.getUser(), job.getContainer(), distinctSubjects, p, newRecords, params.optBoolean("includeAncestors", false));
        }
        pedigreeRecords.addAll(newRecords);

        Collections.sort(pedigreeRecords, new Comparator<PedigreeRecord>()
        {
            @Override
            public int compare(PedigreeRecord o1, PedigreeRecord o2)
            {
                if (o1.subjectName.equals(o2.father) || o1.subjectName.equals(o2.mother))
                    return -1;
                else if (o2.subjectName.equals(o1.father) || o2.subjectName.equals(o1.mother))
                    return 1;
                else if (o1.mother == null && o1.father == null)
                    return -1;
                else if (o2.mother == null && o2.father == null)
                    return 1;

                return o1.subjectName.compareTo(o2.subjectName);
            }
        });

        //morgan doesnt allow IDs with only one parent, so add a dummy ID
        List<PedigreeRecord> toAdd = new ArrayList<>();
        for (PedigreeRecord pd : pedigreeRecords)
        {
            if (StringUtils.isEmpty(pd.father) && !StringUtils.isEmpty(pd.mother))
            {
                pd.father = "xf" + pd.subjectName;
                PedigreeRecord pr = new PedigreeRecord();
                pr.subjectName = pd.father;
                pr.gender = "m";
                toAdd.add(pr);
            }
            else if (!StringUtils.isEmpty(pd.father) && StringUtils.isEmpty(pd.mother))
            {
                pd.mother = "xm" + pd.subjectName;
                PedigreeRecord pr = new PedigreeRecord();
                pr.subjectName = pd.mother;
                pr.gender = "f";
                toAdd.add(pr);
            }
        }

        if (!toAdd.isEmpty())
        {
            job.getLogger().info("adding " + toAdd.size() + " subjects to handle IDs with only one parent known");
            pedigreeRecords.addAll(toAdd);
        }

        job.getLogger().info("total subjects: " + pedigreeRecords.size());

        return pedigreeRecords;
    }

    private static void appendParents(User u, Container c, Set<String> distinctSubjects, PedigreeRecord p, List<PedigreeRecord> newRecords, boolean includeAncestors)
    {
        if (p.father != null && !distinctSubjects.contains(p.father))
        {
            //lookup ancestors
            PedigreeRecord pr = null;
            if (includeAncestors)
            {
                TableInfo subjectTable = QueryService.get().getUserSchema(u, (c.isWorkbook() ? c.getParent() : c), "laboratory").getTable("subjects");
                TableSelector ts = new TableSelector(subjectTable, PageFlowUtil.set("subjectname", "mother", "father", "gender"), new SimpleFilter(FieldKey.fromString("subjectname"), p.father), null);
                pr = ts.getObject(PedigreeRecord.class);
                if (pr.gender == null)
                {
                    pr.gender = "m";
                }
            }

            if (pr == null)
            {
                pr = new PedigreeRecord();
                pr.subjectName = p.father;
                pr.gender = "m";
            }

            newRecords.add(pr);
            distinctSubjects.add(p.father);
            appendParents(u, c, distinctSubjects, pr, newRecords, includeAncestors);
        }

        if (p.mother != null && !distinctSubjects.contains(p.mother))
        {
            //lookup ancestors
            PedigreeRecord pr = null;
            if (includeAncestors)
            {
                TableInfo subjectTable = QueryService.get().getUserSchema(u, (c.isWorkbook() ? c.getParent() : c), "laboratory").getTable("subjects");
                TableSelector ts = new TableSelector(subjectTable, PageFlowUtil.set("subjectname", "mother", "father", "gender"), new SimpleFilter(FieldKey.fromString("subjectname"), p.mother), null);
                pr = ts.getObject(PedigreeRecord.class);
                if (pr.gender == null)
                {
                    pr.gender = "f";
                }
            }

            if (pr == null)
            {
                pr = new PedigreeRecord();
                pr.subjectName = p.mother;
                pr.gender = "f";
            }

            newRecords.add(pr);
            distinctSubjects.add(p.mother);
            appendParents(u, c, distinctSubjects, pr, newRecords, includeAncestors);
        }
    }
}
