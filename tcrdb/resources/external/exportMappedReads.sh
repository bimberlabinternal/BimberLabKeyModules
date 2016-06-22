#!/bin/bash

set -e
set -u
set -x

INPUT_BAM=$1
FASTQ_F=$2
FASTQ_R=$3

ALIGNED=tmp.sam

# exactly one end mapped
$SAMTOOLS view -h -F 4 -f 8 $INPUT_BAM > $ALIGNED
$SAMTOOLS view -f 4 -F 8 $INPUT_BAM >> $ALIGNED

# exactly two ends mapped
$SAMTOOLS view -F 12 $INPUT_BAM >> $ALIGNED

$JAVA -jar $PICARD SamToFastq \
    INPUT=$ALIGNED \
    FASTQ=$FASTQ_F \
    SECOND_END_FASTQ=$FASTQ_R

rm -Rf $ALIGNED
