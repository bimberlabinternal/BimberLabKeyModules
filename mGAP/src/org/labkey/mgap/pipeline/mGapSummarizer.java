package org.labkey.mgap.pipeline;

import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.util.IOUtil;
import htsjdk.variant.variantcontext.VariantContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class mGapSummarizer
{
    public static final List<String> SUMMARY_FIELDS = Arrays.asList("CHROM", "ANN", "CLN_SIG", "FANTOM_TF", "FANTOM_ENHNCRID", "Polyphen2_HVAR_pred");

    public mGapSummarizer()
    {

    }

    private static class FieldTracker
    {
        private final Map<String, FieldData> perField;

        public FieldTracker(int size)
        {
            perField = new HashMap<>(size);
        }

        public void add(String fieldName, String val)
        {
            if (!SUMMARY_FIELDS.contains(fieldName))
            {
                return;
            }

            if (!StringUtils.isEmpty(val) && !"NA".equals(val))
            {
                if ("ANN".equals(fieldName))
                {
                    boolean isHighImpact = false;
                    Set<String> codingPotential = new HashSet<>();
                    String[] tokens = val.split(",");
                    for (String v : tokens)
                    {
                        String[] split = v.split("\\|");
                        if ("HIGH".equals(split[2]))
                        {
                            isHighImpact = true;
                        }

                        String[] types = split[1].split("&");
                        codingPotential.addAll(Arrays.asList(types));
                    }

                    if (isHighImpact)
                    {
                        addForValue("AnnotationSummary", "Predicted High Impact (SnpEff)");
                    }

                    filterCodingPotential(codingPotential);
                    String type = StringUtils.join(new TreeSet<>(codingPotential), ";");
                    addForValue("CodingPotential", type);
                }
                else if ("CLN_SIG".equals(fieldName))
                {
                    boolean hasOverlap = false;
                    boolean hasAnyOverlap = false;
                    String[] tokens = val.split(",");
                    for (String v : tokens)
                    {
                        if (StringUtils.isEmpty(v))
                        {
                            continue;
                        }

                        hasAnyOverlap = true;

                        String[] split = v.split("\\|");
                        for (String sig : split)
                        {
                            //TODO: consider disease = not_provided
                            if (isAllowableClinVarSig(sig))
                            {
                                hasOverlap = true;
                                break;
                            }
                        }
                    }

                    if (hasOverlap)
                    {
                        addForValue("AnnotationSummary", "ClinVar Overlap (Pathogenic)");
                    }

                    if (hasAnyOverlap)
                    {
                        addForValue("AnnotationSummary", "ClinVar Overlap");
                    }
                }
                else if ("FANTOM_ENHNCRID".equals(fieldName) && !StringUtils.isEmpty(val) && !".".equals(val))
                {
                    addForValue("AnnotationSummary", "Enhancer Region (FANTOM5)");
                }
                else if ("FANTOM_TF".equals(fieldName) && !StringUtils.isEmpty(val) && !".".equals(val))
                {
                    addForValue("AnnotationSummary", "Transcription Factor Binding (FANTOM5)");
                }
                else if ("Polyphen2_HVAR_pred".equals(fieldName) && val.contains("D"))
                {
                    addForValue("AnnotationSummary", "Damaging (Polyphen2)");
                }
                else if ("CHROM".equals(fieldName))
                {
                    addForValue("PerChromosome", val);
                }
            }
        }

        private void addForValue(String fieldName, String val)
        {
            if (allowForLevel(val))
            {
                FieldData data = perField.get(fieldName);
                if (data == null)
                {
                    data = new FieldData();
                }

                Integer count = data.countsPerLevel.get(val);
                if (count == null)
                {
                    count = 0;
                }
                count++;

                data.countsPerLevel.put(val, count);

                perField.put(fieldName, data);
            }
        }

        private boolean allowForLevel(String val)
        {
            //if not numeric, accept it
            if (!NumberUtils.isCreatable(val))
            {
                return true;
            }

            //otherwise allow only integers
            return NumberUtils.isDigits(val);
        }
    }

    private static class FieldData
    {
        int nonNull = 0;
        Map<String, Integer> countsPerLevel = new HashMap<>(20);
    }

    public static void filterCodingPotential(Set<String> codingPotential)
    {
        //due to overlapping transcripts, this is often added.  remove these less-specific terms in order
        for (String type : Arrays.asList("intragenic_variant", "non_coding_transcript_variant", "intron_variant"))
        {
            if (codingPotential.size() > 1)
            {
                codingPotential.remove(type);
            }
        }

        if (codingPotential.contains("synonymous_variant") || codingPotential.contains("missense_variant"))
        {
            codingPotential.remove("intragenic_variant");
            codingPotential.remove("non_coding_transcript_variant");
            codingPotential.remove("intron_variant");
        }
    }

    public void generateSummary(SequenceOutputHandler.JobContext ctx, File variantsToTable, File output, File outputPerValue, long totalVariants, long totalPrivateVariants, int totalSubjects, Map<VariantContext.Type, Long> typeCounts) throws PipelineJobException
    {
        ctx.getLogger().info("reading variant table");
        int lineNo = 0;
        FieldTracker tracker = new FieldTracker(130);
        try (BufferedReader reader = Readers.getReader(variantsToTable))
        {
            lineNo++;
            if (lineNo % 500000 == 0)
            {
                ctx.getLogger().info("processed " + lineNo + " lines");
            }

            String lineStr;
            List<String> header = new ArrayList<>();
            int lineCount = 0;
            while ((lineStr = reader.readLine()) != null)
            {
                String[] line = lineStr.split("\t");
                lineCount++;
                if (lineCount == 1)
                {
                    //skip header
                    header = Arrays.asList(line);
                    continue;
                }

                //skip basic site information, but do include CHROM, since that might be useful to see summarized
                for (int i = 4; i < line.length; i++)
                {
                    tracker.add(header.get(i), line[i]);
                }
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        ctx.getLogger().info("writing summary tables");
        try (CSVWriter writer = new CSVWriter(IOUtil.openFileForBufferedWriting(output), '\t', CSVWriter.NO_QUOTE_CHARACTER); CSVWriter valWriter = new CSVWriter(IOUtil.openFileForBufferedWriting(outputPerValue), '\t', CSVWriter.NO_QUOTE_CHARACTER))
        {
            writer.writeNext(new String[]{"Field", "Category", "NonNull", "TotalDistinct", "Levels"});
            for (String fn : new TreeSet<>(tracker.perField.keySet()))
            {
                FieldData data = tracker.perField.get(fn);

                String vals = "";
                if (data.countsPerLevel.size() < 10)
                {
                    vals = StringUtils.join(data.countsPerLevel.keySet(), ",");
                }

                writer.writeNext(new String[]{fn, "None", String.valueOf(data.nonNull), (data.countsPerLevel.isEmpty() ? "" : String.valueOf(data.countsPerLevel.size())), vals});
            }

            valWriter.writeNext(new String[]{"Field", "Level", "Total"});
            valWriter.writeNext(new String[]{"Counts", "TotalVariants", String.valueOf(totalVariants)});
            valWriter.writeNext(new String[]{"Counts", "TotalPrivateVariants", String.valueOf(totalPrivateVariants)});
            valWriter.writeNext(new String[]{"Counts", "TotalSamples", String.valueOf(totalSubjects)});

            for (VariantContext.Type type : typeCounts.keySet())
            {
                valWriter.writeNext(new String[]{"VariantType", type.name(), String.valueOf(typeCounts.get(type))});
            }

            for (String fn : new TreeSet<>(tracker.perField.keySet()))
            {
                FieldData data = tracker.perField.get(fn);
                for (String val : data.countsPerLevel.keySet())
                {
                    valWriter.writeNext(new String[]{fn, val, String.valueOf(data.countsPerLevel.get(val))});
                }
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    public static boolean isAllowableClinVarSig(String x)
    {
        return !(StringUtils.isEmpty(x) || x.toLowerCase().contains("benign") || x.toLowerCase().contains("unknown") || x.toLowerCase().contains("uncertain") || x.contains("not_specified") || x.contains("not_provided"));
    }
}
