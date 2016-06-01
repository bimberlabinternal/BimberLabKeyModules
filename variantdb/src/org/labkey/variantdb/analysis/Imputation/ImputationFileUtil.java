package org.labkey.variantdb.analysis.Imputation;

import com.drew.lang.annotations.Nullable;
import htsjdk.samtools.util.Interval;
import htsjdk.tribble.Feature;

import java.io.File;

/**
 * Created by bimber on 4/28/2016.
 */
public class ImputationFileUtil
{
    public static File getAlleleFreqFile(File alleleFreqDir, MarkerType markerType, String chr, @Nullable Integer denseMarkerBatchIdx)
    {
        return new File(alleleFreqDir, markerType.name() + "." +  chr + (denseMarkerBatchIdx == null ? "" : "-" + denseMarkerBatchIdx) + ".markerFreq.txt");
    }

    public static File getAlleleFreqGenotypesFile(File basedir, MarkerType markerType, String chr, @Nullable Integer denseMarkerBatchIdx)
    {
        return new File(basedir, markerType.name() + "." + chr + (denseMarkerBatchIdx == null ? "" : "-" + denseMarkerBatchIdx) + ".afreq");
    }

    public static File getGiGiGenotypeFile(MarkerType type, File setBaseDir, String chr, String sampleName, GiGiType gigiType, @Nullable Integer denseMarkerBatchIdx)
    {
        setBaseDir = new File(setBaseDir, chr);
        setBaseDir = new File(setBaseDir, "genotypes");
        if (!setBaseDir.exists())
        {
            setBaseDir.mkdirs();
        }
        return new File(setBaseDir, type.name() + "_" + sampleName + "." + gigiType.name() + (denseMarkerBatchIdx == null ? "" : "-" + denseMarkerBatchIdx) + ".geno");
    }

    public static enum MarkerType
    {
        framework(),
        dense()
    }

    public static enum GiGiType
    {
        experimental()
    }

    public static String getMarkerName(Interval i)
    {
        return (i.getSequence() + "_" + i.getStart()).replaceFirst("chr", "");
    }

    public static String getMarkerName(Feature i)
    {
        return (i.getChr() + "_" + i.getStart()).replaceFirst("chr", "");
    }
}
