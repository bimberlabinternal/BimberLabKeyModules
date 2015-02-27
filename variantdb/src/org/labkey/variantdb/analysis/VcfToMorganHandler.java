package org.labkey.variantdb.analysis;

import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFFileReader;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
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
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.variantdb.VariantDBManager;
import org.labkey.variantdb.VariantDBModule;
import org.labkey.variantdb.run.GLAutoRunner;
import org.labkey.variantdb.run.VCFtoMorganConverter;
import picard.vcf.ByIntervalListVariantContextIterator;


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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 2/22/2015.
 */
public class VcfToMorganHandler extends AbstractParameterizedOutputHandler
{
    private final FileType _vcfType = new FileType(Arrays.asList("vcf"), "vcf", false, FileType.gzSupportLevel.SUPPORT_GZ);

    public VcfToMorganHandler()
    {
        super(ModuleLoader.getInstance().getModule(VariantDBModule.class), "Create MORGAN Output", "This will reformat the selected VCF(s) into the marker files necessary to run MORGAN gl_auto and GIGI.", null, null);
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
        return false;
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
            try
            {
                for (SequenceOutputFile f : inputFiles)
                {
                    FileAnalysisJobSupport support = job.getJobSupport(FileAnalysisJobSupport.class);
                    File baseDir = new File(support.getAnalysisDirectory(), SequencePipelineService.get().getUnzippedBaseName(f.getFile().getName()));
                    baseDir.mkdirs();

                    //make ped file
                    List<String> sampleNames = VariantDBManager.get().getSamplesForVcf(f.getFile());
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

                    File gatkPed = new File(baseDir, "gatkPed.ped");
                    File morganPed = new File(baseDir, "morgan.ped");
                    try (BufferedWriter garkWriter = new BufferedWriter(new FileWriter(gatkPed)); BufferedWriter morganWriter = new BufferedWriter(new FileWriter(morganPed)))
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
            try
            {
                for (SequenceOutputFile f : inputFiles)
                {
                    FileAnalysisJobSupport support = job.getJobSupport(FileAnalysisJobSupport.class);
                    File baseDir = new File(support.getAnalysisDirectory(), SequencePipelineService.get().getUnzippedBaseName(f.getFile().getName()));
                    baseDir.mkdirs();

                    VCFtoMorganConverter converter = new VCFtoMorganConverter();
                    converter.convert(f.getFile(), baseDir, "dense", job.getLogger());


                    File seedFile = new File(baseDir, "sampler.seed");
                    try (BufferedWriter seedWriter = new BufferedWriter(new FileWriter(seedFile)))
                    {
                        seedWriter.write("set sampler seeds  0xb69cb2f5 0x562302c9\n");
                    }
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            //new GLAutoRunner(job.getLogger()).execute(glAutoParams);

            throw new PipelineJobException("fail");
        }

        @Override
        public void processFilesRemote(SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

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
