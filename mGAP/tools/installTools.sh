#!/usr/bin/env bash

set -e
set -u
FORCE_REINSTALL=
SKIP_PACKAGE_MANAGER=
CLEAN_SRC=
LK_HOME=
LK_USER=

while getopts "d:u:fpc" arg;
do
  case $arg in
    d)
       LK_HOME=$OPTARG
       LK_HOME=${LK_HOME%/}
       echo "LK_HOME = ${LK_HOME}"
       ;;
    u)
       LK_USER=$OPTARG
       echo "LK_USER = ${LK_USER}"
       ;;
    f)
       FORCE_REINSTALL=1
       ;;
    p)
       SKIP_PACKAGE_MANAGER=1
       echo "SKIP_PACKAGE_MANAGER = ${SKIP_PACKAGE_MANAGER}"
       ;;
    c)
       CLEAN_SRC=1
       echo "CLEAN_SRC = ${CLEAN_SRC}"
       ;;
    *)
       echo "The following arguments are supported:"
       echo "-d: the path to the labkey install, such as /usr/local/labkey.  If only this parameter is provided, tools will be installed in bin/ and src/ under this location."
       echo "-u: optional.  The OS user that will own the downloaded files.  Defaults to labkey"
       echo "-f: optional.  If provided, all tools will be reinstalled, even if already present"
       echo "-p: optional. "
       echo "Example command:"
       echo "./sequence_tools_install.sh -d /usr/local/labkey"
       exit 1;
      ;;
  esac
done

if [ -z $LK_HOME ];
then
    echo "Must provide the install location using the argument -d"
    exit 1;
fi

LKTOOLS_DIR=${LK_HOME}/bin
LKSRC_DIR=${LK_HOME}/tool_src
mkdir -p $LKSRC_DIR
mkdir -p $LKTOOLS_DIR

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install location"
echo ""
echo "LKTOOLS_DIR: $LKTOOLS_DIR"
echo "LKSRC_DIR: $LKSRC_DIR"
WGET_OPTS="--read-timeout=10 --secure-protocol=TLSv1"

if [[ ! -e ${LKTOOLS_DIR}/annovar || ! -z $FORCE_REINSTALL ]];
then
    rm -Rf annovar*
    rm -Rf ${LKTOOLS_DIR}/annovar*

    rm -Rf Cassandra*
    rm -Rf ${LKTOOLS_DIR}/Cassandra*

    rm -Rf dataSourcesApr15*
    rm -Rf ${LKTOOLS_DIR}/dataSourcesApr15*

    wget $WGET_OPTS http://www.openbioinformatics.org/annovar/download/0wgxR2rIVP/annovar.latest.tar.gz
    tar -xf annovar.latest.tar.gz

    cd annovar
    perl annotate_variation.pl --buildver hg19 -downdb refGene humandb/
    perl annotate_variation.pl --buildver hg19 -downdb knownGene humandb/
    perl annotate_variation.pl --buildver hg19 -downdb ensGene humandb/
    perl annotate_variation.pl --buildver hg19 -downdb -webfrom annovar clinvar_20170130 humandb/

    perl annotate_variation.pl --buildver hg38 -downdb -webfrom annovar clinvar_20170130 humandb/

    cd ../
    mv annovar ${LKTOOLS_DIR}/annovar

    wget $WGET_OPTS ftp://ftp.hgsc.bcm.edu/Software/Cassandra/version_15.4.10/Cassandra.jar
    mv Cassandra.jar ${LKTOOLS_DIR}/Cassandra.jar

    wget $WGET_OPTS ftp://ftp.hgsc.bcm.edu/Software/Cassandra/version_15.4.10/dataSourcesApr15.tar.gz

    cd annovar

    wget $WGET_OPTS http://www.openbioinformatics.org/annovar/download/GRCh37_MT_ensGene.txt
    wget $WGET_OPTS http://www.openbioinformatics.org/annovar/download/GRCh37_MT_ensGeneMrna.fa
fi