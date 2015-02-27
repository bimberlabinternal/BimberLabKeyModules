package org.labkey.variantdb.analysis;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
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
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.util.FileType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.variantdb.VariantDBModule;
import org.labkey.variantdb.run.SelectVariantsWrapper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    public static class Processor implements OutputProcessor
    {
        @Override
        public void init(PipelineJob job, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            //find genome
            Set<Integer> ids = new HashSet<>();
            for (SequenceOutputFile f : inputFiles)
            {
                ids.add(f.getLibrary_id());
            }

            if (ids.size() != 1)
            {
                throw new PipelineJobException("The selected files use more than 1 genome.  All VCFs must use the same genome");
            }

            //make ped file
            FileAnalysisJobSupport support = job.getJobSupport(FileAnalysisJobSupport.class);
            Set<String> sampleNames = new HashSet<>();
            for (String name : Arrays.asList("targets", "complete", "alleleFrequency"))
            if (params.containsKey(name))
            {
                for (Object o : params.getJSONArray(name).toArray())
                {
                    sampleNames.add(o.toString());
                }
            }

            UserSchema us = QueryService.get().getUserSchema(job.getUser(), (job.getContainer().isWorkbook() ? job.getContainer().getParent() : job.getContainer()), "laboratory");
            TableInfo ti = us.getTable("subjects");
            SimpleFilter filter = new SimpleFilter();
            filter.addClause(new SimpleFilter.OrClause(
                    new SimpleFilter.InClause(FieldKey.fromString("subjectname"), sampleNames),
                    new SimpleFilter.InClause(FieldKey.fromString("mother"), sampleNames),
                    new SimpleFilter.InClause(FieldKey.fromString("father"), sampleNames)
            ));

            final List<PedigreeRecord> pedigreeRecords = new ArrayList<>();
            TableSelector ts = new TableSelector(ti, PageFlowUtil.set("subjectname", "mother", "father", "gender"), filter, null);
            ts.forEach(new Selector.ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet rs) throws SQLException
                {
                    PedigreeRecord pedigree = new PedigreeRecord();
                    pedigree.subjectName = rs.getString("subjectname");
                    pedigree.father = rs.getString("father");
                    pedigree.mother = rs.getString("mother");
                    if (!StringUtils.isEmpty(pedigree.subjectName))
                        pedigreeRecords.add(pedigree);
                }
            });

            Collections.sort(pedigreeRecords, new Comparator<PedigreeRecord>()
            {
                @Override
                public int compare(PedigreeRecord o1, PedigreeRecord o2)
                {
                    return o1.subjectName.equals(o2.father) || o1.subjectName.equals(o2.mother) ? 1 : o1.subjectName.compareTo(o2.subjectName);
                }
            });

            File gatkPed = new File(support.getAnalysisDirectory(), "gatkPed.ped");
            File morganPed = new File(support.getAnalysisDirectory(), "morgan.ped");
            try (BufferedWriter garkWriter = new BufferedWriter(new FileWriter(gatkPed));BufferedWriter morganWriter = new BufferedWriter(new FileWriter(morganPed)))
            {
                morganWriter.write("input pedigree size " + pedigreeRecords.size() + '\n');
                morganWriter.write("*****" + '\n');
                for (PedigreeRecord pd : pedigreeRecords)
                {
                    List<String> vals = Arrays.asList(pd.subjectName, (StringUtils.isEmpty(pd.father) ? "0" : pd.father), (StringUtils.isEmpty(pd.mother) ? "0" : pd.mother), ("m".equals(pd.gender) ? "1" : "f".equals(pd.gender) ? "2" : "0"), "0");
                    morganWriter.write(StringUtils.join(vals, " ") + '\n');
                    garkWriter.write("FAM01 " + StringUtils.join(vals, " ") + '\n');
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            for (SequenceOutputFile o : inputFiles)
            {
                List<String> selectParams = new ArrayList<>();
                if (params.containsKey("selectType"))
                {
                    selectParams.add("-selectType");
                    selectParams.add(params.getString("selectType"));
                }

                if (params.containsKey("restrictAllelesTo"))
                {
                    selectParams.add("-restrictAllelesTo");
                    selectParams.add(params.getString("restrictAllelesTo"));
                }

                if (params.containsKey("restrictAllelesTo"))
                {
                    selectParams.add("-restrictAllelesTo");
                    selectParams.add(params.getString("restrictAllelesTo"));
                }

                SelectVariantsWrapper sv = new SelectVariantsWrapper(support.getJob().getLogger());
                File selectOut = new File(o.getFile().getParentFile(), SequencePipelineService.get().getUnzippedBaseName(o.getFile().getName()) + ".select.vcf.gz");
                //TODO: genome
                sv.execute(o.getFile(), selectOut, null, selectParams);

                //filter
                List<String> filterParams = new ArrayList<>();
                if (params.containsKey("cluster"))
                {
                    filterParams.add("-cluster");
                    filterParams.add(params.getString("cluster"));
                }

                if (params.containsKey("clusterSize"))
                {
                    filterParams.add("-window");
                    filterParams.add(params.getString("clusterSize"));
                }

                if (params.containsKey("filterExpression"))
                {
                    filterParams.add("--filterExpression");
                    filterParams.add(params.getString("filterExpression"));
                    filterParams.add("--filterName");
                    filterParams.add("Filter");

                }

                SelectVariantsWrapper fv = new SelectVariantsWrapper(support.getJob().getLogger());
                File filterOut = new File(o.getFile().getParentFile(), SequencePipelineService.get().getUnzippedBaseName(o.getFile().getName()) + ".filtered.vcf.gz");
                //TODO: genome
                sv.execute(o.getFile(), filterOut, null, filterParams);

                //mendelian check
                if (params.optBoolean("mendelianCheck", false))
                {
                    //TODO
                }


            }


            if (params.containsKey("frameworkFilters"))
            {
                JSONArray filterArr = params.getJSONArray("frameworkFilters");
                for (int i=0;i<filterArr.length();i++)
                {
                    JSONArray arr = filterArr.getJSONArray(i);
                    Integer dataId = arr.getInt(0);
                    ExpData d = ExperimentService.get().getExpData(dataId);
                    if (d == null)
                    {
                        throw new PipelineJobException("Unable to find file with Id: " + dataId);
                    }

                    String type = arr.getString(1);
                    if ("exclude".equals(type))
                    {

                    }


                }
            }

            throw new PipelineJobException("fail");

        }

        private class PedigreeRecord
        {
            String subjectName;
            String father;
            String mother;
            String gender;


        }
    }
}
