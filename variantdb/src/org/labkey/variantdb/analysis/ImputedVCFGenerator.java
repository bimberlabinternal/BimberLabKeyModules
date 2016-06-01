package org.labkey.variantdb.analysis;

import com.drew.lang.annotations.Nullable;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFConstants;
import htsjdk.variant.vcf.VCFFormatHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import htsjdk.variant.vcf.VCFStandardHeaderLines;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by bimber on 4/27/2016.
 */
public class ImputedVCFGenerator
{
    public static String IMPUTATION_PROBABILITIES = "IMP_PROB";
    public static String IMPUTATION_PROBABILITY = "IP";
    public static String IMPUTATION_PROBABILITY_DIFF = "IPD";
    public static String IS_IMPUTED = "IMP";

    public static String TOTAL_NON_CALLED_REF = "NCR";
    public static String INCORRECT_IMPUTATION_GENOTYPES = "IIG";
    public static String IMPUTATION_SKIPPED_GENOTYPES = "ISG";
    public static String CORRECT_IMPUTATION_GENOTYPES = "CIG";
    public static String TRUE_GENOTYPE_SOURCE = "TGS";
    public static String TRUE_GENOTYPE_BASES = "TGB";
    public static String TRUE_GENOTYPE_QUAL = "TGQ";

    public static String INCORECT_IMPUTATION = "IncorrectImputation";

    public ImputedVCFGenerator()
    {

    }

    public static Genotype generateGenotype(String sn, List<Double> probabilities, List<String> imputedGenos, List<String> alleleToBase, @Nullable Genotype refGenotype, @Nullable Genotype imputationInputGenotype, List<String> trueGenos, String referenceVcfFileName, boolean isIncorrect)
    {
        GenotypeBuilder gb = new GenotypeBuilder();
        gb.name(sn);

        List<Allele> alleles = new ArrayList<>();
        for (String geno : imputedGenos)
        {
            int genoInt = Integer.parseInt(geno);
            if (genoInt > 0)
            {
                alleles.add(Allele.create(alleleToBase.get(genoInt - 1), genoInt == 1));
            }
            else
            {
                alleles.add(Allele.NO_CALL);
            }
        }
        gb.alleles(alleles);

        gb.attribute(IMPUTATION_PROBABILITY, Collections.max(probabilities));
        gb.attribute(IMPUTATION_PROBABILITIES, StringUtils.join(probabilities, ","));

        List<Double> sorted = new ArrayList<>(probabilities);
        Collections.sort(sorted);
        gb.attribute(IMPUTATION_PROBABILITY_DIFF, (sorted.get(sorted.size() - 1) - sorted.get(sorted.size() - 2)));
        gb.attribute(IS_IMPUTED, ((imputationInputGenotype != null && imputationInputGenotype.isNoCall()) ? 1 : 0));

        if (refGenotype != null)
        {
            gb.attribute(TRUE_GENOTYPE_QUAL, refGenotype.getPhredScaledQual());
            List<String> refBases  = new ArrayList<>();
            for (Allele a : refGenotype.getAlleles())
            {
                refBases.add(a.getBaseString());
            }

            gb.attribute(TRUE_GENOTYPE_BASES, StringUtils.join(refBases, ","));
            gb.attribute(TRUE_GENOTYPE_SOURCE, referenceVcfFileName);
        }

        if (isIncorrect)
        {
            gb.attribute(INCORECT_IMPUTATION, INCORECT_IMPUTATION);
        }

        return gb.make();
    }

    public static VariantContextWriter getVariantWriter(File file, ReferenceGenome genome, List<String> sampleNames)
    {
        VariantContextWriterBuilder builder = new VariantContextWriterBuilder();
        builder.setOutputFile(file);

        Set<VCFHeaderLine> metaLines = new HashSet<>();
        VCFStandardHeaderLines.addStandardInfoLines(metaLines, false, Arrays.asList(
                VCFConstants.END_KEY,
                VCFConstants.DBSNP_KEY,
                VCFConstants.DEPTH_KEY,
                VCFConstants.STRAND_BIAS_KEY,
                VCFConstants.ALLELE_FREQUENCY_KEY,
                VCFConstants.ALLELE_COUNT_KEY,
                VCFConstants.ALLELE_NUMBER_KEY,
                VCFConstants.MAPPING_QUALITY_ZERO_KEY,
                VCFConstants.RMS_MAPPING_QUALITY_KEY,
                VCFConstants.SOMATIC_KEY
        ));

        VCFStandardHeaderLines.addStandardFormatLines(metaLines, false, Arrays.asList(
                VCFConstants.GENOTYPE_KEY,
                VCFConstants.GENOTYPE_QUALITY_KEY,
                VCFConstants.DEPTH_KEY,
                VCFConstants.GENOTYPE_PL_KEY,
                VCFConstants.GENOTYPE_ALLELE_DEPTHS,
                VCFConstants.GENOTYPE_FILTER_KEY,
                VCFConstants.PHASE_QUALITY_KEY
        ));

        metaLines.add(new VCFFormatHeaderLine(IMPUTATION_PROBABILITY, 1, VCFHeaderLineType.Float, "This holds the maximum probability assigned to this genotype call from GIGI"));
        metaLines.add(new VCFFormatHeaderLine(IMPUTATION_PROBABILITIES, 1, VCFHeaderLineType.Character, "This holds all probabilities assigned to genotype calls by GIGI"));
        metaLines.add(new VCFFormatHeaderLine(IMPUTATION_PROBABILITY_DIFF, 1, VCFHeaderLineType.Float, "This holds the difference between the two most likely probability assignments from GIGI"));
        metaLines.add(new VCFFormatHeaderLine(IS_IMPUTED, 1, VCFHeaderLineType.Integer, "A flag to mark imputed genotypes.  1 denotes imputed, 0 is not."));
        metaLines.add(new VCFFormatHeaderLine(TRUE_GENOTYPE_QUAL, 1, VCFHeaderLineType.Float, "The quality of the reference genotype used to score this imputation result"));
        metaLines.add(new VCFFormatHeaderLine(TRUE_GENOTYPE_SOURCE, 1, VCFHeaderLineType.Character, "The filename of the VCF file holding the reference data for this imputation result"));
        metaLines.add(new VCFFormatHeaderLine(TRUE_GENOTYPE_BASES, 1, VCFHeaderLineType.Character, "The bases from the true genotype used to evaluate this imputed genotype"));

        metaLines.add(new VCFInfoHeaderLine(INCORRECT_IMPUTATION_GENOTYPES, 1, VCFHeaderLineType.Integer, "The number of genotypes incorrectly imputed"));
        metaLines.add(new VCFInfoHeaderLine(IMPUTATION_SKIPPED_GENOTYPES, 1, VCFHeaderLineType.Integer, "The number of genotypes where imputation was skipped"));
        metaLines.add(new VCFInfoHeaderLine(CORRECT_IMPUTATION_GENOTYPES, 1, VCFHeaderLineType.Integer, "The number of genotypes correctly imputed"));
        metaLines.add(new VCFInfoHeaderLine(TOTAL_NON_CALLED_REF, 1, VCFHeaderLineType.Integer, "The number of subjects with non-called reference for imputation"));

        metaLines.add(new VCFFormatHeaderLine(INCORECT_IMPUTATION, 1, VCFHeaderLineType.Character, "Marks incorrectly imputed genotypes"));

        VCFHeader header = new VCFHeader(metaLines, sampleNames);

        SAMSequenceDictionary dict = SAMSequenceDictionaryExtractor.extractDictionary(new File(genome.getWorkingFastaFile().getParent(), FileUtil.getBaseName(genome.getWorkingFastaFile().getName()) + ".dict"));
        builder.setReferenceDictionary(dict);
        header.setSequenceDictionary(dict);

        VariantContextWriter writer = builder.build();
        writer.writeHeader(header);

        return writer;
    }
}
