package org.labkey.variantdb.analysis;

import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.Interval;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFFileReader;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
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
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.variantdb.VariantDBModule;
import org.labkey.variantdb.run.BedtoolsRunner;
import org.labkey.variantdb.run.ImputationRunner;
import org.labkey.variantdb.run.SelectVariantsWrapper;

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
            Set<String> sampleNames = new HashSet<>();
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
                            sampleNames.add(set.getString(0));
                        }
                    }
                }
            }

            final List<PedigreeRecord> pedigreeRecords = new ArrayList<>();
            TableSelector ts = new TableSelector(getSubjectTable(job.getUser(), job.getContainer()), PageFlowUtil.set("subjectname", "mother", "father", "gender"), new SimpleFilter(FieldKey.fromString("subjectname"), sampleNames, CompareType.IN), null);
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
            for (SequenceOutputFile f : inputFiles)
            {
                String basename = FileUtil.getBaseName(f.getFile());
                if (gz.isType(f.getFile()))
                {
                    basename = FileUtil.getBaseName(basename);
                }

                RecordedAction action = new RecordedAction(getName());
                actions.add(action);
                action.addInput(gatkPed, "Pedigree File");

                File baseDir = new File(job.getJobSupport(FileAnalysisJobSupport.class).getAnalysisDirectory(), SequencePipelineService.get().getUnzippedBaseName(f.getFile().getName()));
                baseDir.mkdirs();

                File inputVcf = f.getFile();
                File vcfFiltered = null;

                //essentially allows resume on failed jobs
                File postBedtools = new File(baseDir, basename + ".mendelianPass.vcf");
                if (params.optBoolean("skipMendelianCheck", false))
                {

                }
                else if (postBedtools.exists())
                {
                    inputVcf = postBedtools;
                }
                else
                {
                    //identify non-mendelian SNPs
                    job.getLogger().info("identifying non-mendelian SNPs");
                    ReferenceGenome genome = support.getCachedGenome(f.getLibrary_id());
                    SelectVariantsWrapper selectVariantsWrapper = new SelectVariantsWrapper(job.getLogger());
                    File nonMendelianVcf = new File(baseDir, basename + ".mendelianViolations.vcf.gz");
                    selectVariantsWrapper.execute(genome.getSourceFastaFile(), f.getFile(), nonMendelianVcf, Arrays.asList("-mv", "-mvq", "50", "-pedValidationType", "SILENT", "-ped", gatkPed.getPath()));
                    action.addOutput(nonMendelianVcf, "Mendelian Violation SNPs", false, true);
                    int nonMendelian = 0;

                    job.getLogger().info("counting non-mendelian SNPs");
                    try (VCFFileReader reader = new VCFFileReader(nonMendelianVcf, false))
                    {
                        try (CloseableIterator<VariantContext> it = reader.iterator())
                        {
                            while (it.hasNext())
                            {
                                it.next();
                                nonMendelian++;
                            }
                        }
                    }
                    job.getLogger().info("total non-mendelian SNPs: " + nonMendelian);

                    //then use bedtools to remove violations
                    if (nonMendelian > 0)
                    {
                        job.getLogger().info("removing non-mendelian SNPs");
                        vcfFiltered = postBedtools;
                        action.addOutput(nonMendelianVcf, "Mendelian SNPs", true, true);
                        BedtoolsRunner bt = new BedtoolsRunner(job.getLogger());
                        bt.execute(Arrays.asList(bt.getExe().getPath(), "intersect", "-v", "-header", "-sorted", "-a", f.getFile().getPath(), "-b", nonMendelianVcf.getPath()), vcfFiltered);

                        inputVcf = vcfFiltered;
                    }
                }

                File denseMarkers = support.getCachedData(params.getInt("denseFile"));
                action.addInput(denseMarkers, "Dense Markers");
                File frameworkMarkers = support.getCachedData(params.getInt("frameworkFile"));
                action.addInput(frameworkMarkers, "Framework Markers");

                action.addInput(f.getFile(), "Variant Data");

                job.getLogger().info("starting imputation:");
                File summary = new File(baseDir, "summary.txt");
                action.addOutput(summary, "Summary Table", false, true);

                File errorSummary = new File(baseDir, "errorSummary.txt");
                action.addOutput(errorSummary, "Error Summary Table", false, true);

                Map<String, PedigreeRecord> pedigreeRecordMap = parsePedigree(gatkPed);
                job.getLogger().debug("pedigree size: " + pedigreeRecordMap.size());

                try (CSVWriter writer = new CSVWriter(new FileWriter(summary), '\t', CSVWriter.NO_QUOTE_CHARACTER);CSVWriter errorWriter = new CSVWriter(new FileWriter(errorSummary), '\t', CSVWriter.NO_QUOTE_CHARACTER))
                {
                    writer.writeNext(new String[]{"SetName", "CompleteGenotypes", "ImputedSubjects", "Subject", "CallMethod", "Chr", "TotalMarkers", "TotalMatching", "TotalMissing", "PctMatching", "PctMissing", "NumFirstOrderRelativesWithWGS", "NumFirstOrderRelativesPresent", "FirstOrderRelativesWithWGS", "FirstOrderRelativesPresent", "TotalImputed"});
                    errorWriter.writeNext(new String[]{"SetName", "CompleteGenotypes", "ImputedSubjects", "TotalImputed", "Subject", "MarkerNumber", "Chr", "Position", "ImputedGenotype", "ActualGenotype", "PreviousFramework", "DistanceFromPreviousFramework", "NextFramework", "DistanceFromNextFramework", "DistanceFromNearestMarker", "NumNearbyMarkers", "FirstOrderRelativesWithWGS", "FirstOrderRelativesPresent"});

                    List<Pair<List<String>, List<String>>> sets = getSampleSets(params);
                    Integer idx = 0;
                    for (Pair<List<String>, List<String>> samples : sets)
                    {
                        idx++;
                        job.getLogger().info("imputing set " + idx + " of " + sets.size());
                        job.setStatus("Imputing set " + idx + " of " + sets.size());

                        File dir = new File(baseDir, idx.toString());
                        dir.mkdirs();

                        ImputationRunner converter = new ImputationRunner(denseMarkers, frameworkMarkers, job.getLogger());
                        String callMethod = params.get("callMethod") != null ? params.getString("callMethod") : "1";
                        converter.processSet(inputVcf, dir, "Set" + idx.toString(), job.getLogger(), samples.first, samples.second, callMethod);

                        //calculate relatives present in WGS per subject:
                        Map<String, Set<String>> relativesPresent = new TreeMap<>();
                        Map<String, Set<String>> wgsRelativesPresent = new TreeMap<>();
                        Set<PedigreeRecord> wgsSubjects = new HashSet<>();
                        Set<PedigreeRecord> allSubjects = new HashSet<>();
                        for (String id : samples.first)
                        {
                            wgsSubjects.add(pedigreeRecordMap.get(id));
                            allSubjects.add(pedigreeRecordMap.get(id));
                        }

                        for (String id : samples.second)
                        {
                            allSubjects.add(pedigreeRecordMap.get(id));
                        }

                        for (String imputedSubj : samples.second)
                        {
                            PedigreeRecord pr = pedigreeRecordMap.get(imputedSubj);
                            if (pr == null)
                            {
                                throw new PipelineJobException("unable to find pedigree record for id: [" + imputedSubj + "]");
                            }

                            relativesPresent.put(imputedSubj, pr.getRelativesPresent(allSubjects));
                            wgsRelativesPresent.put(imputedSubj, pr.getRelativesPresent(wgsSubjects));
                        }

                        for (String chr : converter.getDenseChrs())
                        {
                            File imputed = new File(dir, chr + "/impute.geno");
                            List<Interval> frameworkIntervals = converter.getFrameworkIntervals(chr);
                            try (BufferedReader imputedReader = new BufferedReader(new FileReader(imputed)))
                            {
                                String imputedLine;
                                OUTER: while ((imputedLine = imputedReader.readLine()) != null)
                                {
                                    String[] imputedData = imputedLine.split("( )+");
                                    String[] trueGenotypesData = null;
                                    File trueGenotypes = new File(dir, chr + "/denseComplete.geno");
                                    try (BufferedReader trueGenotypereader = new BufferedReader(new FileReader(trueGenotypes)))
                                    {
                                        String trueGenotypesLine;
                                        INNER: while ((trueGenotypesLine = trueGenotypereader.readLine()) != null)
                                        {
                                            String[] lineData = trueGenotypesLine.split("( )+");
                                            if (lineData[0].equals(imputedData[0]))
                                            {
                                                trueGenotypesData = lineData;
                                                break INNER;
                                            }
                                        }
                                    }

                                    if (!samples.second.contains(imputedData[0]))
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
                                        throw new IOException("reference and imputed genotypes files do not have the same number of markers: " + imputed.getPath());
                                    }

                                    int totalMarkers = 0;
                                    int totalMatching = 0;
                                    int totalMissing = 0;
                                    final int WINDOW_SIZE = 800000;  //~4X the avg distance
                                    for (int i = 1; i < trueGenotypesData.length; i++)
                                    {
                                        totalMarkers++;
                                        totalMarkers++; //we are cycling through 2 positions
                                        List<String> imputedGenos = new ArrayList<>(Arrays.asList(imputedData[i], imputedData[i + 1]));
                                        Collections.sort(imputedGenos);
                                        List<String> trueGenos = new ArrayList<>(Arrays.asList(trueGenotypesData[i], trueGenotypesData[i + 1]));
                                        Collections.sort(trueGenos);

                                        i++; //additional increment for 2nd marker

                                        if (imputedGenos.get(0).equals("0"))
                                        {
                                            totalMissing++;
                                        }
                                        else if (trueGenos.get(0).equals(imputedGenos.get(0)))
                                        {
                                            totalMatching++;
                                        }

                                        if (imputedGenos.get(1).equals("0"))
                                        {
                                            totalMissing++;
                                        }
                                        else if (trueGenos.get(1).equals(imputedGenos.get(1)))
                                        {
                                            totalMatching++;
                                        }

                                        if ((!trueGenos.get(0).equals(imputedGenos.get(0)) || !trueGenos.get(1).equals(imputedGenos.get(1))) || (!imputedGenos.get(0).equals("0") || !imputedGenos.get(1).equals("0")))
                                        {
                                            int intervalIdx = i / 2;  //there are 2 markers per genome position.  this is 1-based
                                            Interval errorIv = converter.getDensePositionByIndex(chr, intervalIdx);
                                            if (errorIv == null)
                                            {
                                                job.getLogger().error("Unable to find dense interval for idx: " + i);
                                            }
                                            else
                                            {
                                                //find markers near to this position
                                                int nearbyMarkers = 0;
                                                for (Interval iv : frameworkIntervals)
                                                {
                                                    int d1 = Math.abs(iv.getStart() - errorIv.getStart());
                                                    if (d1 < WINDOW_SIZE)
                                                    {
                                                        nearbyMarkers++;
                                                    }
                                                }

                                                boolean found = false;
                                                Interval previous = null;
                                                for (Interval iv : frameworkIntervals)
                                                {
                                                    if (iv.getStart() > errorIv.getStart())
                                                    {
                                                        Integer d1 = previous == null ? null : errorIv.getStart() - previous.getStart();
                                                        Integer d2 = iv.getStart() - errorIv.getStart();

                                                        errorWriter.writeNext(new String[]{
                                                                idx.toString(),
                                                                StringUtils.join(samples.first, ";"),
                                                                StringUtils.join(samples.second, ";"),
                                                                String.valueOf(samples.second.size()),
                                                                imputedData[0],
                                                                String.valueOf(intervalIdx),
                                                                chr,
                                                                String.valueOf(errorIv.getStart()),
                                                                StringUtils.join(imputedGenos, ";"),
                                                                StringUtils.join(trueGenos, ";"),
                                                                (previous != null ? String.valueOf(previous.getStart()) : "no marker"),
                                                                (previous != null ? String.valueOf(d1) : "no marker"),
                                                                String.valueOf(iv.getStart()),
                                                                String.valueOf(d2),
                                                                String.valueOf(d1 == null ? d2 : Math.min(d1, d2)),
                                                                String.valueOf(nearbyMarkers),
                                                                String.valueOf(wgsRelativesPresent.get(imputedData[0]).size()),
                                                                String.valueOf(relativesPresent.get(imputedData[0]).size())
                                                        });

                                                        found = true;
                                                        break;
                                                    }

                                                    previous = iv;
                                                }

                                                if (!found)
                                                {
                                                    errorWriter.writeNext(new String[]{
                                                            idx.toString(),
                                                            StringUtils.join(samples.first, ";"),
                                                            StringUtils.join(samples.second, ";"),
                                                            String.valueOf(samples.second.size()),
                                                            imputedData[0],
                                                            String.valueOf(intervalIdx),
                                                            chr,
                                                            String.valueOf(errorIv.getStart()),
                                                            StringUtils.join(imputedGenos, ";"),
                                                            StringUtils.join(trueGenos, ";"),
                                                            (previous != null ? String.valueOf(previous.getStart()) : "no marker"),
                                                            (previous != null ? String.valueOf(errorIv.getStart() - previous.getStart()) : "No marker"),
                                                            "No marker",
                                                            "No marker",
                                                            String.valueOf(previous == null ? "" : errorIv.getStart() - previous.getStart()),
                                                            String.valueOf(nearbyMarkers),
                                                            String.valueOf(wgsRelativesPresent.get(imputedData[0]).size()),
                                                            String.valueOf(relativesPresent.get(imputedData[0]).size())
                                                    });
                                                }
                                            }
                                        }
                                    }

                                    writer.writeNext(new String[]{
                                            idx.toString(),
                                            StringUtils.join(samples.first, ";"),
                                            StringUtils.join(samples.second, ";"),
                                            imputedData[0],
                                            callMethod,
                                            chr,
                                            String.valueOf(totalMarkers),
                                            String.valueOf(totalMatching),
                                            String.valueOf(totalMissing),
                                            String.valueOf((double) totalMatching / (totalMarkers - totalMissing)),
                                            String.valueOf((double) totalMissing / totalMarkers),
                                            String.valueOf(wgsRelativesPresent.get(imputedData[0]).size()),
                                            String.valueOf(relativesPresent.get(imputedData[0]).size()),
                                            StringUtils.join(wgsRelativesPresent.get(imputedData[0]), ";"),
                                            StringUtils.join(relativesPresent.get(imputedData[0]), ";"),
                                            String.valueOf(samples.second.size())
                                    });
                                }
                            }
                        }
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                if (vcfFiltered != null)
                {
                    File index = new File(vcfFiltered.getPath() + ".idx");
                    if (index.exists())
                    {
                        index.delete();
                    }

                    vcfFiltered.delete();

                }
            }
        }

        private void appendParents(User u, Container c, Set<String> distinctSubjects, PedigreeRecord p, List<PedigreeRecord> newRecords, boolean includeAncestors)
        {
            if (p.father != null && !distinctSubjects.contains(p.father))
            {
                //lookup ancestors
                PedigreeRecord pr = null;
                if (includeAncestors)
                {
                    TableSelector ts = new TableSelector(getSubjectTable(u, c), PageFlowUtil.set("subjectname", "mother", "father", "gender"), new SimpleFilter(FieldKey.fromString("subjectname"), p.father), null);
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
                    TableSelector ts = new TableSelector(getSubjectTable(u, c), PageFlowUtil.set("subjectname", "mother", "father", "gender"), new SimpleFilter(FieldKey.fromString("subjectname"), p.mother), null);
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

        private List<Pair<List<String>, List<String>>> getSampleSets(JSONObject params)
        {
            List<Pair<List<String>, List<String>>> ret = new ArrayList<>();

            if (params.containsKey("sampleSets"))
            {
                for (Object o : params.getJSONArray("sampleSets").toArray())
                {
                    JSONArray arr = (JSONArray)o;
                    List<String> complete = new ArrayList<>();
                    JSONArray completeSet = arr.getJSONArray(0);
                    for (int j=0;j<completeSet.length();j++)
                    {
                        complete.add(completeSet.getString(j));
                    }

                    List<String> impute = new ArrayList<>();
                    JSONArray imputeSet = arr.getJSONArray(1);
                    for (int j=0;j<imputeSet.length();j++)
                    {
                        impute.add(imputeSet.getString(j));
                    }

                    Collections.sort(complete);
                    Collections.sort(impute);

                    ret.add(Pair.of(complete, impute));
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
}
