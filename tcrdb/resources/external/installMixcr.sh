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

#
#mixcr
#

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Installing MiXCR"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/mixcr || ! -z $FORCE_REINSTALL ]];
then
    rm -Rf mixcr*
    rm -Rf $LKTOOLS_DIR/mixcr*
    rm -Rf $LKTOOLS_DIR/libraries
    rm -Rf $LKTOOLS_DIR/importFromIMGT.sh

    wget $WGET_OPTS https://github.com/milaboratory/mixcr/releases/download/v3.0.2/mixcr-3.0.2.zip
    unzip mixcr-3.0.2.zip

    install ./mixcr-3.0.2/mixcr $LKTOOLS_DIR/mixcr
    install ./mixcr-3.0.2/mixcr.jar $LKTOOLS_DIR/mixcr.jar
    cp -R ./mixcr-3.0.2/libraries $LKTOOLS_DIR

else
    echo "Already installed"
fi


#
#vdjtools
#

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Installing vdjtools"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/vdjtools.jar || ! -z $FORCE_REINSTALL ]];
then
    rm -Rf vdjtools*
    rm -Rf $LKTOOLS_DIR/vdjtools*

    wget $WGET_OPTS https://github.com/mikessh/vdjtools/releases/download/1.1.9/vdjtools-1.1.9.zip
    unzip vdjtools-1.1.9.zip

    install ./vdjtools-1.1.9/vdjtools-1.1.9.jar $LKTOOLS_DIR/vdjtools.jar
    install ./vdjtools-1.1.9/vdjtools $LKTOOLS_DIR/vdjtools

else
    echo "Already installed"
fi

