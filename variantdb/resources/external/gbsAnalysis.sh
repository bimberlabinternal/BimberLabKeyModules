#!/bin/bash

set -e
set -u
set -x

VCF=
MASK=
CUT_SITES=
REF=
LK_DIR=
HISTOGRAM_SCRIPT=/usr/local/labkey/modules/SequenceAnalysis/external/basicHistogram.r

while getopts "v:i:m:c:l:h:r:" arg;
do
  case $arg in
    v)
       VCF=$OPTARG
       echo "VCF = ${VCF}"
       ;;
    i)
       INPUT=$OPTARG
       echo "INPUT = ${INPUT}"
       ;;
    m)
       MASK=$OPTARG
       echo "MASK = ${MASK}"
       ;;
    r)
       REF=$OPTARG
       echo "REF = ${REF}"
       ;;
    c)
       CUT_SITES=$OPTARG
       echo "CUT_SITES = ${CUT_SITES}"
       ;;
    l)
       LK_DIR=$OPTARG
       echo "LK_DIR = ${LK_DIR}"
       ;;
    h)
       HISTOGRAM_SCRIPT=$OPTARG
       echo "HISTOGRAM_SCRIPT = ${HISTOGRAM_SCRIPT}"
       ;;
	*)
       echo "unknown: ["$arg"]"
   esac
done

BASENAME=`basename "$INPUT" .bam`

echo "Starting: "$BASENAME
echo "Starting: "$BASENAME >> coverage_summary.txt

if [ ! -e "${BASENAME}_insertSize.pdf" ];
then
	echo "calculating insert size"
	java -jar ${LK_DIR}/picard.jar CollectInsertSizeMetrics O=${BASENAME}_insertSize.txt H=${BASENAME}_insertSize.pdf I="${INPUT}"
fi

if [ ! -e "${BASENAME}_coverage.bed"  ];
then
	echo "calculating coverage"
	${LK_DIR}/bedtools genomecov -ibam "${INPUT}" -dz > ${BASENAME}_coverage.txt
	echo "creating BED"
	awk -v OFS='\t' '{ print $1,$2,($2 + 1),$3}' ${BASENAME}_coverage.txt > ${BASENAME}_coverage.bed
	echo "Coverage "$(wc -l ${BASENAME}_coverage.bed) >> coverage_summary.txt
	rm ${BASENAME}_coverage.txt
fi

declare -a arr=("3" "20" "30")
for DEPTH in "${arr[@]}"
do
    if [ ! -e "${BASENAME}_cutSites_${DEPTH}.png" ];
    then
        echo "calculating "$DEPTH"X coverage"
        awk -v d=$DEPTH ' $4 >= d ' ${BASENAME}_coverage.bed > ${BASENAME}_coverage_${DEPTH}.bed
        echo "Coverage1X"$DEPTH"X "$(wc -l ${BASENAME}_coverage_"$DEPTH".bed) >> coverage_summary.txt

        if [ ! -z $MASK ]; then
            echo "calculating repeat overlap: "$DEPTH"X"
            ${LK_DIR}/bedtools intersect -a ${BASENAME}_coverage_${DEPTH}.bed -b "${MASK}" -v -sorted > ${BASENAME}_repeatNonOverlap_${DEPTH}.bed
            echo "RepeatNonOverlap"$DEPTH"X "$(grep -v '^#' ${BASENAME}_repeatNonOverlap_${DEPTH}.bed | wc -l)" "$BASENAME >> coverage_summary.txt
            rm ${BASENAME}_repeatNonOverlap_${DEPTH}.bed
        fi

        if [ ! -z $VCF ]; then
            echo "calculating VCF overlap: "$DEPTH"X"
            ${LK_DIR}/bedtools intersect -a ${BASENAME}_coverage_${DEPTH}.bed -b "${VCF}" -sorted > ${BASENAME}_vcfOverlap_${DEPTH}.bed
            echo "VcfOverlap"$DEPTH"X "$(grep -v '^#' ${BASENAME}_vcfOverlap_${DEPTH}.bed | wc -l)" "$BASENAME >> coverage_summary.txt

            #echo "calculating reference sites that overlap GBS coverage: "$DEPTH"X"
            #java -jar ${LK_DIR}/GenomeAnalysisTK.jar -T VariantFiltration -V "${VCF}" -mask "${BASENAME}_vcfOverlap_${DEPTH}.bed" -maskName "NoGBSCoverage" --filterNotInMask -R "${REF}" -o "${BASENAME}_masked_${DEPTH}.vcf.gz"
            #java -jar ${LK_DIR}/GenomeAnalysisTK.jar -T SelectVariants -R "${REF}" -V "${BASENAME}_masked_${DEPTH}.vcf.gz" -ef -o "${BASENAME}_covered_${DEPTH}.vcf.gz"
            #rm "${BASENAME}_masked_${DEPTH}.vcf.gz"

            rm "${BASENAME}_vcfOverlap_${DEPTH}.bed"
        fi

        if [ ! -z $CUT_SITES ]; then
            echo "calculating cut site overlap: "$DEPTH"X"
            ${LK_DIR}/bedtools closest -a ${BASENAME}_coverage_${DEPTH}.bed -b "${CUT_SITES}" -d > ${BASENAME}_cutSites_${DEPTH}.txt
            Rscript ${HISTOGRAM_SCRIPT} -i ${BASENAME}_cutSites_${DEPTH}.txt -o ${BASENAME}_cutSites_${DEPTH}.png -t "Distance From Cut Site: ${DEPTH}X" -c 4 --binWidth 1 -h FALSE
            gzip -f ${BASENAME}_cutSites_${DEPTH}.txt
        fi

        echo "joining contiguous covered positions: "$DEPTH"X, allowing a 10 NT gap"
        ${LK_DIR}/bedtools merge -i "${BASENAME}_coverage_${DEPTH}.bed" -d 10 > "${BASENAME}_coverage_merged_${DEPTH}.bed"
        echo "MergedIntervals"$DEPTH"X "$(grep -v '^#' ${BASENAME}_coverage_merged_${DEPTH}.bed | wc -l)" "$BASENAME >> coverage_summary.txt

        awk -v OFS='\t' '{ print ($3 - $2) }' "${BASENAME}_coverage_merged_${DEPTH}.bed" > "${BASENAME}_coverage_merged_lengths_${DEPTH}.txt"
        Rscript ${HISTOGRAM_SCRIPT} -i "${BASENAME}_coverage_merged_lengths_${DEPTH}.txt" -o "${BASENAME}_fragment_length_${DEPTH}.png" -t "GBS Fragment Length: ${DEPTH}X" -c 1 --binWidth 1 -h FALSE
        rm -Rf "${BASENAME}_coverage_merged_lengths_${DEPTH}.txt"

        echo "calculating distance between GBS sites: "$DEPTH"X"
        ${LK_DIR}/bedtools closest -a "${BASENAME}_coverage_merged_${DEPTH}.bed" -b "${BASENAME}_coverage_merged_${DEPTH}.bed" -io -d > "${BASENAME}_coverage_merged_${DEPTH}.txt"
        Rscript ${HISTOGRAM_SCRIPT} -i "${BASENAME}_coverage_merged_${DEPTH}.txt" -o "${BASENAME}_coverage_merged_distance_${DEPTH}.png" -t "Distance From Next GBS Fragment: ${DEPTH}X" -c 7 --binWidth 100 --maxValue 250000 -h FALSE
        Rscript ${HISTOGRAM_SCRIPT} -i "${BASENAME}_coverage_merged_${DEPTH}.txt" -o "${BASENAME}_coverage_merged_per_chromosome_${DEPTH}.png" -t "GBS Fragments Per Chromosome: ${DEPTH}X" -c 1 --binWidth 1 -h FALSE

        rm ${BASENAME}_coverage_${DEPTH}.bed
        gzip ${BASENAME}_coverage_merged_${DEPTH}.bed
        gzip -f ${BASENAME}_coverage_merged_${DEPTH}.txt
    fi
done

gzip ${BASENAME}_coverage.bed
