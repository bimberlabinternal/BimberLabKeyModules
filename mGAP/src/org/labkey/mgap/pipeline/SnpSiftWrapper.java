package org.labkey.mgap.pipeline;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 8/24/2016.
 */
public class SnpSiftWrapper extends AbstractCommandWrapper
{
    public SnpSiftWrapper(Logger log)
    {
        super(log);
    }

    public void runSnpSift(File dbnsfpFile, File input, File output) throws PipelineJobException
    {
        getLogger().info("Annotating VCF with SnpSift");

        List<String> params = new ArrayList<>();
        params.add(SequencePipelineService.get().getJavaFilepath());
        params.addAll(SequencePipelineService.get().getJavaOpts());
        params.add("-jar");
        params.add(getSnpSiftJar().getPath());
        params.add("DbNsfp");
        params.add("-db");
        params.add(dbnsfpFile.getPath());

        params.add("-noDownload");

        params.add("-f");
        params.add("SIFT_score,SIFT_converted_rankscore,SIFT_pred,SIFT4G_score,SIFT4G_converted_rankscore,SIFT4G_pred,Polyphen2_HDIV_score,Polyphen2_HDIV_rankscore,Polyphen2_HDIV_pred,Polyphen2_HVAR_score,Polyphen2_HVAR_rankscore,Polyphen2_HVAR_pred,LRT_score,LRT_converted_rankscore,LRT_pred,LRT_Omega,MutationTaster_score,MutationTaster_converted_rankscore,MutationTaster_pred,MutationTaster_model,MutationTaster_AAE,MutationAssessor_score,MutationAssessor_rankscore,MutationAssessor_pred,FATHMM_score,FATHMM_converted_rankscore,FATHMM_pred,PROVEAN_score,PROVEAN_converted_rankscore,PROVEAN_pred,VEST4_score,VEST4_rankscore,MetaSVM_score,MetaSVM_rankscore,MetaSVM_pred,MetaLR_score,MetaLR_rankscore,MetaLR_pred,Reliability_index,M-CAP_score,M-CAP_rankscore,M-CAP_pred,REVEL_score,REVEL_rankscore,MutPred_score,MutPred_rankscore,MutPred_protID,MutPred_AAchange,MutPred_Top5features,MVP_score,MVP_rankscore,MPC_score,MPC_rankscore,PrimateAI_score,PrimateAI_rankscore,PrimateAI_pred,DEOGEN2_score,DEOGEN2_rankscore,DEOGEN2_pred,BayesDel_addAF_score,BayesDel_addAF_rankscore,BayesDel_addAF_pred,BayesDel_noAF_score,BayesDel_noAF_rankscore,BayesDel_noAF_pred,LIST-S2_score,LIST-S2_rankscore,LIST-S2_pred,Aloft_Fraction_transcripts_affected,Aloft_prob_Tolerant,Aloft_prob_Recessive,Aloft_prob_Dominant,Aloft_pred,Aloft_Confidence,DANN_score,DANN_rankscore,fathmm-MKL_coding_score,fathmm-MKL_coding_rankscore,fathmm-MKL_coding_pred,fathmm-MKL_coding_group,fathmm-XF_coding_score,fathmm-XF_coding_rankscore,fathmm-XF_coding_pred,Eigen-raw_coding,Eigen-raw_coding_rankscore,Eigen-phred_coding,Eigen-PC-raw_coding,Eigen-PC-raw_coding_rankscore,Eigen-PC-phred_coding,GenoCanyon_score,GenoCanyon_rankscore,integrated_fitCons_score,integrated_fitCons_rankscore,integrated_confidence_value,GM12878_fitCons_score,GM12878_fitCons_rankscore,GM12878_confidence_value,H1-hESC_fitCons_score,H1-hESC_fitCons_rankscore,H1-hESC_confidence_value,HUVEC_fitCons_score,HUVEC_fitCons_rankscore,HUVEC_confidence_value,LINSIGHT,LINSIGHT_rankscore,GERP++_NR,GERP++_RS,GERP++_RS_rankscore,phyloP100way_vertebrate,phyloP100way_vertebrate_rankscore,phyloP30way_mammalian,phyloP30way_mammalian_rankscore,phyloP17way_primate,phyloP17way_primate_rankscore,phastCons100way_vertebrate,phastCons100way_vertebrate_rankscore,phastCons30way_mammalian,phastCons30way_mammalian_rankscore,phastCons17way_primate,phastCons17way_primate_rankscore");

        params.add(input.getPath());

        File unzippedVcf = new File(getOutputDir(output), "snpSift.vcf");
        execute(params, unzippedVcf);

        if (!unzippedVcf.exists())
        {
            throw new PipelineJobException("output not found: " + unzippedVcf.getName());
        }

        unzippedVcf = SequenceAnalysisService.get().bgzipFile(unzippedVcf, getLogger());
        try
        {
            if (!unzippedVcf.equals(output))
            {
                if (output.exists())
                {
                    getLogger().debug("deleting pre-existing output file: " + output.getPath());
                    output.delete();
                }
                FileUtils.moveFile(unzippedVcf, output);
            }
            SequenceAnalysisService.get().ensureVcfIndex(output, getLogger());
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private File getJarDir()
    {
        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("SNPEFFPATH");
        if (path != null)
        {
            return new File(path, "snpEff");
        }

        path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath(SequencePipelineService.SEQUENCE_TOOLS_PARAM);
        if (path == null)
        {
            path = PipelineJobService.get().getAppProperties().getToolsDirectory();
        }

        return path == null ? new File("snpEff") : new File(path, "snpEff");
    }

    public File getSnpSiftJar()
    {
        return new File(getJarDir(), "SnpSift.jar");
    }
}
