#!/bin/bash
# Linux From SCRIPT - Build a Linux operating system from sourcecode
# Copyright (C) 2007-2014 Marcel van den Boer
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
#
trap handleErrors ERR
trap manualAbort  INT
set -E
handleErrors() {
  local EXITSTATUS="$?"
  displayMsg ERR "A command stopped with exit status: ${EXITSTATUS}"
  queryAbort
}
queryAbort() {
  if [ -r "/sources/buildmgr/autoabort" ]; then
    TMP_AUTOABORT="$(cat /sources/buildmgr/autoabort)"
  fi
  if [ "${TMP_AUTOABORT}" == "1" ]; then
    displayMsg KEY WARN "-A switch used; Automatically aborting..."
    manualAbort
  else
    displayMsg KEY WARN "Press 'CTRL+C' to abort, or 'ENTER' to continue..."
    read PRESSENTER
  fi
}
delayErrors() {
  ERRPAUSE="pause"
}
handleErrorsNow() {
  if [ "${ERRORSFOUND}" == "yes" ]; then
    displayMsg ERR "Errors were detected while ${1}."
    queryAbort
  fi
  ERRORSFOUND="no"
  ERRPAUSE="resume"
}
manualAbort() {
  if [ -w "/sources/buildmgr" ]; then
    touch /sources/buildmgr/abort
    chmod 666 /sources/buildmgr/abort
  fi
  exitCleanup
  exit
}
exitCleanup() {
  if [ ! -e "/buildmgr.root" ]; then
    if [ "$(whoami)" == "root" ]; then
      if [ -e "/sources/buildmgr/cleanexit" ]; then
        displayMsg KEY "Restoring environment for next package..."
      else
        displayMsg KEY ERR "Aborting..."
      fi
      if [ -r "/sources" ]; then
        displayMsg "... Restoring system state."
        eval ${ROOTVARIABLE}="$(cat /sources/buildmgr/rootvar)"
        buildbase${LASTFUNCTION} &> /dev/null || echo -n ''
        cleanUp
      fi
    fi
  fi
}
cleanUp() {
  if [ -e "/sources/buildmgr/cleanexit" ]; then
    local NEXTRUN="1"
  else
    local NEXTRUN="0"
  fi
  if [ -r /sources/buildmgr/newpackages ]; then
    displayMsg "... Saving newly created package archives."
    cd /sources/buildmgr/newpackages
    cp -R * "$(cat /sources/buildmgr/packsdir)" 2> /dev/null || echo -n ''
  fi
  displayMsg "... Saving logs."
  cd /sources/buildmgr/logs
  mv * "$(cat /sources/buildmgr/logsdir)" 2> /dev/null || echo -n ''
  displayMsg "... Removing temporary directories."
  userdel -r ${BUILDUSER} 2> /dev/null || echo -n ''
  local FROOT="$(cat /sources/buildmgr/rootvar)"
  local SECINSTDIR="$(cat /sources/buildmgr/secinstalldir)"
  cd /
  umount "${FROOT}/sources/buildmgr/packages" 2> /dev/null || echo -n ""
  umount "${FROOT}/sources/src"               2> /dev/null || echo -n ""
  umount "${FROOT}/sources"                   2> /dev/null || echo -n ""
  umount "${FROOT}/tools"                     2> /dev/null || echo -n ""
  rm -rf "${FROOT}/tools"
  rm -rf "${FROOT}/sources"
  rm -rf "${FROOT}/buildmgr.root"
  if [ "${SECINSTDIR}" != "" ]; then
    rm -rf "${SECINSTDIR}"
  fi
  rm -rf "${FROOT}.2"
  rm -rf "${FROOT}"
  rm -rf /tools
  rm -rf /sources
  if [ "${NEXTRUN}" == "1" ]; then
    cd "${STARTDIR}"
    displayMsg KEY "Starting next round."
    deployScripts
  fi
}
displayMsg() {
  echo -e "$@" 1>&2
}
which() {
  type -pa "$@" | head -n 1 ; return ${PIPESTATUS[0]}
}
chroot() {
  $(which chroot) $@ /sources/buildmgr/buildmgr --resumebasebuild
}
exec() {
  if [ "$(echo $@ | grep '/bin/bash --login +h$' || echo -n '')" != "" ]; then
    builtin exec $@ /sources/buildmgr/buildmgr --resumebasebuild
  else
    builtin exec $@
  fi
}
passwd() {
  echo "${1}:secret" | chpasswd
}
strip() {
  $(which strip) $@ &> /dev/null || echo -n ''
}
wget() {
  $(which wget) --no-check-certificate $@ 2> /dev/null
}
su() {
  local SU_USR="$(echo $@ | sed 's/- //')"
  displayMsg KEY "Changing privileges (su): Now running as user '${SU_USR}'"
  $(which su) $@ -c "/sources/buildmgr/buildmgr --resumebasebuild"
  displayMsg KEY "Restoring privileges: Now running as user '$(whoami)'"
}
source() {
  if [ "$(echo $@ | grep '/.bash_profile$' || echo -n '')" != "" ]; then
    echo "/sources/buildmgr/buildmgr --resumebasebuild" >> ~/.bashrc
    echo "exit"                                         >> ~/.bashrc
  fi
  . "$@"
}
lfclass() {
    case $(uname -m) in
    i?86)
        VM="vm-i386"
        ;;
    arm*)
        VM="vm-arm"
        ;;
    *)
        VM="vm-$(uname -m)"
        ;;
    esac
    LFC="lfc/build"
    if [ ! -x "${LFC}/${VM}" ]; then
        LFC="/sources/buildmgr/lfc/${VMNAME}"
        if [ ! -x "${LFC}/${VM}" ]; then
            displayMsg ERR "Could not find an LFClass binary for your CPU."
            echo "exit"
        fi
    fi
    ${LFC}/${VM} -cp ${LFC}/libLFClass.jar org.lfscript.ExecArbiter ${@}
}
resumeBaseBuild() {
  local NEXTFUNCTION="$(( $(cat /sources/buildmgr/currentfunction) + 1 ))"
  echo "${NEXTFUNCTION}" > /sources/buildmgr/currentfunction
  local BASESYSTEM="$(cat /sources/buildmgr/basesystem)"
  . "/sources/buildmgr/scripts/${BASESYSTEM}/buildbase.lfs"
  if [ ! -e "/buildmgr.root" ]; then
    eval ${ROOTVARIABLE}="$(cat /sources/buildmgr/rootvar)"
  fi
  if [ "${NEXTFUNCTION}" == "${RESUMEBACKUP}" ]; then
    if [ "${TMP_BACKUP_TC}" == "1" ]; then
      displayMsg "Backing up the toolchain."
      local ROOTVARC="$(cat /sources/buildmgr/rootvar)"
      chown -R root:root ${ROOTVARC}/tools
      cd "${ROOTVARC}"
      mkdir -p "/sources/buildmgr/newpackages/${BASESYSTEM}"
      tar --xz -cf \
        "/sources/buildmgr/newpackages/${BASESYSTEM}/toolchain.bak.txz" tools
      cd "${OLDPWD}"
      TMP_BACKUP_TC="0"
    fi
  fi
  if [ "${NEXTFUNCTION}" == "${BUILDBEYOND}" ]; then
    umount /tools 2> /dev/null || echo -n ""
  fi
  buildbase${NEXTFUNCTION} 1> /dev/null
  if [ -e "/sources/buildmgr/abort" ]; then
    exitCleanup
    exit
  fi
  if [ "${NEXTFUNCTION}" == "${BUILDBEYOND}" ]; then
    local BUILD_CONF="$(cat /sources/buildmgr/extrapacks)"
    for script in ${BUILD_CONF}; do
      BUILD ${script}
    done
    if [ "$(cat /sources/buildmgr/installdir)" != "" ]; then
      if [ -d /etc/skel ]; then
        displayMsg KEY "Re-creating /root from /etc/skel..."
        rm -rf /root
        cp -R /etc/skel /root
        chmod 0750 /root
        chown -R root:root /root
      fi
      displayMsg KEY "Running remaining post-installation scripts."
      if [ -r "/sources/buildmgr/scripts/install.conf" ]; then
        . /sources/buildmgr/scripts/install.conf
      fi
      local PDIR="/sources/buildmgr/postinst"
      for p in $(ls -1 ${PDIR}/*.sh 2> /dev/null || echo -n ''); do
        displayMsg "... $(echo $(basename ${p}) | cut -d'.' -f1)"
        . ${p}
        delayErrors
        echo "Postinstallation of ${p}..."     1>> \
            /sources/buildmgr/logs/install.log 2>> \
            /sources/buildmgr/logs/install.log
        postinst 1>> /sources/buildmgr/logs/install.log \
                 2>> /sources/buildmgr/logs/install.log
        handleErrorsNow "running a post-installation script"
      done
      if [ "${NOT_INTERACTIVE}" == "" ]; then
        displayMsg KEY "Running interactive configuration (if applicable)..."
        (lfclass iconfig) 1>&2
        displayMsg KEY "Changing the password for 'root'..."
        $(which passwd) root 1>&2
      fi
    fi
  fi
}
deployScripts() {
  local TMP_BUILDDIR="${PWD}/builddir.$$.tmp"
  displayMsg "Locating scripts and resolving dependencies..."
  if [ ! -r "${SCRIPTSDIR}" ]; then
    displayMsg ERR "Scripts directory is not accessible."
    echo "exit"
  fi
  if [ "${TMP_CLEANENV}" == "1" ]; then
    local MODE="next"
  else
    local MODE="all"
  fi
  pushd "${SCRIPTSDIR}/../" &> /dev/null
  rm -rf .excludePkgs.tmp
  for zzz in ${TMP_SKIP}; do
    echo "${zzz}" >> .excludePkgs.tmp
  done
  DEPENDSLIST="$(lfclass dep ${MODE} scripts packages-$(uname -m) \
        .excludePkgs.tmp REQUIRES ${TMP_DEPLEVELS} packs \
        ${TMP_EXTRAPACKS} || echo -n '\fail\')"
  rm -rf .excludePkgs.tmp
  popd &> /dev/null
  if [ "${DEPENDSLIST}" == '\fail\' ]; then
    displayMsg ERR "Unable to create a dependency list."
    echo "exit"
  fi
  if [ "${TMP_LISTONLY}" == "1" ]; then
    displayMsg KEY "Listing dependencies:"
    local depcount="0"
    for dependency in ${DEPENDSLIST}; do
      local depcount="$(( ${depcount} + 1 ))"
      if [ "${depcount}" -le "9" ]; then
        local prefix="   "
      elif [ "${depcount}" -le "99" ]; then
        local prefix="  "
      elif [ "${depcount}" -le "999" ]; then
        local prefix=" "
      fi
      displayMsg "${prefix}${depcount}. ${dependency}"
    done
    displayMsg "Finished listing dependencies for target."
    exit 0
  fi
  . "${SCRIPTSDIR}/${TMP_BASESYSTEM}/buildbase.lfs"
  CONTAINS="${TMP_BASESYSTEM}/${CONTAINS}"
  CONTAINS=$(echo ${CONTAINS} | sed "s@ @ ${TMP_BASESYSTEM}/@g")
  local PRECOMPILED=""
  local TOBECOMPILED=""
  for pack in ${CONTAINS} ${DEPENDSLIST}; do
    local DONTCOPY="0"
    for dontuse in ${TMP_RESUMEX}; do
      if [ "$(basename ${dontuse})" == "$(basename ${pack})" ]; then
        DONTCOPY="1"
      fi
    done
    if [ -r "$(echo ${PACKSDIR}/${pack}.txz)" ] && [ "${DONTCOPY}" != "1" ]
    then
      local PRECOMPILED="${PRECOMPILED} ${pack}"
    else
      local TOBECOMPILED="${TOBECOMPILED} ${pack}"
    fi
  done
  if [ "${TMP_RESUME}" != "1" ]; then
    local TOBECOMPILED="${CONTAINS} ${DEPENDSLIST}"
    if [ "${PRECOMPILED}" != "" ] && [ "${TMP_SOURCESONLY}" != "1" ]; then
      displayMsg WARN "You are about to build a system from scratch. However,"
      displayMsg WARN "by doing this you will overwrite packages that have been"
      displayMsg WARN "built earlier. Are you sure you want to do this?"
      displayMsg WARN ""
      displayMsg WARN "If you wanted to continue building your system based on"
      displayMsg WARN "the existing packages, you should add '-u' to the"
      displayMsg WARN "arguments."
      queryAbort
    fi
  elif [ ! -r "${PACKSDIR}/${TMP_BASESYSTEM}/toolchain.bak.txz" ]; then
    if [ "${TMP_SECINSTALL}" != "" ]; then
      displayMsg ERR "No toolchain backup was found."
      displayMsg ERR "The '-I' switch requires a precompiled toolchain."
      echo "exit"
    fi
    local TOBECOMPILED="${CONTAINS} ${DEPENDSLIST}"
    displayMsg WARN "No toolchain backup was found. Any precompiled packages"
    displayMsg WARN "that have been found will be used at a later stage, but"
    displayMsg WARN "the temporary system needs to be rebuilt anyway."
    displayMsg WARN ""
    displayMsg WARN "Using precompiled packages while also rebuilding the"
    displayMsg WARN "temporary system may have undesirable effects."
    queryAbort
  fi
  displayMsg KEY "Downloading and/or verifying source code..."
  getSources ${TOBECOMPILED}
  if [ "${TMP_SOURCESONLY}" == "1" ]; then
    displayMsg "Finished downloading source code."
    exit 0
  fi
  if [ "$(whoami)" != "root" ]; then
    displayMsg ERR "You can only build a system as the root user."
    echo "exit"
  fi
  mkdir -p "${PACKSDIR}";
  if [ ! -w "${PACKSDIR}" ]; then
    displayMsg ERR "Package directory is not writable:"
    displayMsg ERR "${PACKSDIR}"
    echo "exit"
  fi
  mkdir -p "${LOGSDIR}/${TMP_UID}";
  if [ ! -w "${LOGSDIR}/${TMP_UID}" ]; then
    displayMsg ERR "Logs directory is not writable:"
    displayMsg ERR "${LOGSDIR}/${TMP_UID}"
    echo "exit"
  fi
  if [ -e "/sources" ]; then
    displayMsg ERR "Directory '/sources' already exists."
    echo "exit"
  fi
  for dir in "${EXTERNAL_FOLDERS}"; do
    if [ -e "${dir}" ]; then
      displayMsg ERR "Directory '${dir}' already exists."
      echo "exit"
    fi
  done
  if [ -e "${TMP_BUILDDIR}" ]; then
    displayMsg ERR "Build directory already exists."
    echo "exit"
  fi
  if [ -e "${TMP_BUILDDIR}.2" ]; then
    displayMsg ERR "Secondary build directory already exists."
    echo "exit"
  fi
  if [ -e "${TMP_SECINSTALL}/sec.$$.tmp" ]; then
    displayMsg ERR "Secondary build directory contents already exist."
    echo "exit"
  fi
  local BUEX="$(grep ^${BUILDUSER}: /etc/passwd 2> /dev/null || echo -n '')"
  if [ "${BUEX}" != "" ]; then
    displayMsg ERR "User '${BUILDUSER}' already exists on this system."
    echo "exit"
  fi
  if [ "${TMP_INSTALL}" != "" ]; then
    ln -s "${TMP_INSTALL}" "${TMP_BUILDDIR}"
  fi
  if [ "${TMP_SECINSTALL}" != "" ]; then
    ln -s "${TMP_SECINSTALL}" "${TMP_BUILDDIR}.2"
    mkdir "${TMP_BUILDDIR}.2/sec.$$.tmp"
    mkdir "${TMP_BUILDDIR}.2/sec.$$.tmp/tools"
    mkdir -p "${TMP_BUILDDIR}/tools"
    mount --bind "${TMP_BUILDDIR}.2/sec.$$.tmp/tools" "${TMP_BUILDDIR}/tools"
  fi
  mkdir -p "${TMP_BUILDDIR}/sources/src"
  mkdir -p "${TMP_BUILDDIR}/sources/buildmgr"
  mkdir -p "${TMP_BUILDDIR}/sources/buildmgr/packages"
  mkdir -p "${TMP_BUILDDIR}/sources/buildmgr/postinst"
  mkdir -p "${TMP_BUILDDIR}/sources/buildmgr/logs"
  chmod 777 "${TMP_BUILDDIR}/sources/buildmgr/logs"
  chmod a+wt "${TMP_BUILDDIR}/sources"
  chmod a+wt "${TMP_BUILDDIR}/sources/buildmgr"
  ln -sf "${TMP_BUILDDIR}/sources" /
  touch "${TMP_BUILDDIR}/buildmgr.root"
  mount --bind             "${SOURCESDIR}" /sources/src
  mount -o remount,ro,bind "${SOURCESDIR}" /sources/src
  cp -R lfc/build /sources/buildmgr/lfc
  echo "${DEPENDSLIST}"               > /sources/buildmgr/extrapacks
  echo "${TMP_BUILDDIR}"              > /sources/buildmgr/rootvar
  echo "${TMP_BASESYSTEM}"            > /sources/buildmgr/basesystem
  echo "${TMP_RESUME}"                > /sources/buildmgr/resume
  echo "${TMP_CLEANENV}"              > /sources/buildmgr/cleanenv
  echo "${PACKSDIR}"                  > /sources/buildmgr/packsdir
  echo "${LOGSDIR}/${TMP_UID}"        > /sources/buildmgr/logsdir
  echo "${TMP_AUTOABORT}"             > /sources/buildmgr/autoabort
  echo "${TMP_INSTALL}"               > /sources/buildmgr/installdir
  echo "${TMP_BUILDDIR}.2/sec.$$.tmp" > /sources/buildmgr/secinstalldir
  if [ -r "lfscript" ]; then
    cp "lfscript" "/sources/buildmgr/buildmgr"
  else
    local INSTALLBIN="/usr/bin"
    cp "${INSTALLBIN}/lfscript" "/sources/buildmgr/buildmgr"
  fi
  cp -R "${SCRIPTSDIR}" "/sources/buildmgr/scripts"
  if [ "${TMP_KERNELCONFIG}" != "" ]; then
    cp "${TMP_KERNELCONFIG}" "/sources/buildmgr/kernel.config"
  fi
  if [ "${TMP_RESUME}" == "1" ]; then
    mount --bind             "${PACKSDIR}" /sources/buildmgr/packages
    mount -o remount,ro,bind "${PACKSDIR}" /sources/buildmgr/packages
    for pack in ${PRECOMPILED}; do
      local distro="$(echo ${pack} | cut -d'/' -f1)"
      mkdir -p "/sources/buildmgr/install-packages/${distro}"
      touch "/sources/buildmgr/install-packages/${pack}.txz"
    done
    displayMsg KEY "Restoring toolchain from backup..."
    if [ -r "${PACKSDIR}/${TMP_BASESYSTEM}/toolchain.bak.txz" ]; then
      cd "${TMP_BUILDDIR}"
      tar --xz -xf "${PACKSDIR}/${TMP_BASESYSTEM}/toolchain.bak.txz"
      cd "${OLDPWD}"
      echo "$(( ${RESUMEBACKUP} - 1 ))" > /sources/buildmgr/currentfunction
    else
      displayMsg KEY WARN "No backup found. Rebuilding the temporary system."
      TMP_BACKUP_TC="1"
      echo "0" > /sources/buildmgr/currentfunction
    fi
  else
    echo "0" > /sources/buildmgr/currentfunction
  fi
  chmod 666 /sources/buildmgr/currentfunction
  local CURRFUNCTIONFILE="/sources/buildmgr/currentfunction"
  while [ "$(( $(cat ${CURRFUNCTIONFILE}) + 1 ))" -le "${LASTFUNCTION}" ]; do
    resumeBaseBuild
  done
  displayMsg KEY "Execution finished."
  cleanUp
}
getSources() {
  local MISSINGMD5LIST=()
  for script in $@; do
    WGETLIST=""
    MD5SUMLIST=""
    . "${SCRIPTSDIR}/${script}"
    local FILECOUNTER="0"
    for url in ${WGETLIST}; do
      local FILECOUNTER="$(( ${FILECOUNTER} + 1 ))"
      local THISFILE="$(basename ${url})"
      local THISMD5="$(echo ${MD5SUMLIST} | cut -d' ' -f ${FILECOUNTER})"
      if [ ! -e "${SOURCESDIR}/${THISFILE}" ]; then
        if [ ! -w "${SOURCESDIR}" ]; then
          displayMsg ERR "Sources directory is not writable:"
          displayMsg ERR "${SOURCESDIR}"
          echo "exit"
        fi
        cd "${SOURCESDIR}"
        local ALTSOURCEDIR="/usr/src"
        if [ -r "${ALTSOURCEDIR}/${THISFILE}" ]; then
          displayMsg "... Copying '${THISFILE}' from a local source."
          cp "${ALTSOURCEDIR}/${THISFILE}" .
          chmod 644 ${THISFILE}
        else
          displayMsg "... Downloading '${THISFILE}'."
          wget ${url} || DLERROR="$?"
        fi
        cd "${OLDPWD}"
        if [ "${DLERROR}" == "127" ]; then
          displayMsg ERR "You do not have the 'wget' utility installed."
          displayMsg ERR "Therefore you should download all the required"
          displayMsg ERR "sources manually... Or install 'wget'."
          echo "exit"
        elif [ "${DLERROR}" != "" ]; then
          DLERROR=""
          displayMsg WARN "...... Primary URL is down. Trying a mirror."
          
          cd "${SOURCESDIR}"
          rm -rf ${THISFILE}
          wget http://www.lfscript.org/sources/$(basename ${url}) || DLERR="$?"
          cd "${OLDPWD}"
          if [ "${DLERR}" != "" ]; then
            displayMsg ERR "...... Unable to download file (Code ${DLERR})."
            DLERR=""
          fi
        fi
      fi
      local THISFILE="${SOURCESDIR}/${THISFILE}"
      if [ "${THISMD5}" != "dontverify" ]; then
        local CHECKSUM="$(md5sum ${THISFILE} 2> /dev/null || echo -n '')"
        if [ "${CHECKSUM}" == "" ]; then
          local NALIST="${NALIST} $(basename ${url})"
        elif [ "${CHECKSUM}" != "${THISMD5}  ${THISFILE}" ]; then
          displayMsg "... MD5 checksum failed for '$(basename ${url})'."
          displayMsg WARN "...... Deleting file and trying a mirror."
          
          cd "${SOURCESDIR}"
          rm -rf ${THISFILE}
          wget http://www.lfscript.org/sources/$(basename ${url}) || DLERR="$?"
          cd "${OLDPWD}"
          if [ "${DLERR}" != "" ]; then
            displayMsg ERR "...... Unable to download file (Code ${DLERR})."
            DLERR=""
          fi
          local CHECKSUM="$(md5sum ${THISFILE} 2> /dev/null || echo -n '')"
          if [ "${CHECKSUM}" == "" ]; then
            local NALIST="${NALIST} $(basename ${url})"
          elif [ "${CHECKSUM}" != "${THISMD5}  ${THISFILE}" ]; then
            local MD5FAILEDLIST="${MD5FAILEDLIST} ${THISFILE}"
            displayMsg ERR "MD5 checksum failed for '$(basename ${url})'."
          else
            displayMsg "... Verified '$(basename ${url})'."
          fi
        else
          displayMsg "... Verified '$(basename ${url})'."
        fi
      else
        if [ ! -r "${THISFILE}" ]; then
          local NALIST="${NALIST} $(basename ${url})"
          displayMsg ERR "The file '$(basename ${url})' is not available."
        else
          if [ "${TMP_LISTMD5}" == "1" ]; then
            displayMsg WARN "... Calculating checksum for '$(basename ${url})'."
            local CHECKSUM="$(md5sum ${THISFILE} 2> /dev/null | cut -d' ' -f1)"
            MISSINGMD5LIST+=("${CHECKSUM}:$(basename ${url})")
          else
            displayMsg WARN "... Didn't verify '$(basename ${url})'."
          fi
        fi
      fi
    done
  done
  if [ "${MD5FAILEDLIST}" != "" ]; then
    displayMsg KEY ERR "The following sources are possibly corrupted (their"
    displayMsg     ERR "checksums are not the same as defined in the scripts):"
    for failed in ${MD5FAILEDLIST}; do
      displayMsg ERR " - ${failed}"
    done
    displayMsg ERR "Please remove these files manually before trying again."
  fi
  if [ "${NALIST}" != "" ]; then
    displayMsg KEY ERR "The following sources are not available:"
    for failed in ${NALIST}; do
      displayMsg ERR " - ${failed}"
    done
    displayMsg ERR "If you have a working network connection, the URL's for"
    displayMsg ERR "these files need to be updated in their scripts."
  fi
  if [ "${TMP_LISTMD5}" == "1" ] && [ "${#MISSINGMD5LIST[@]}" != "0" ]; then
    displayMsg KEY "The following checksums were calculated for files without"
    displayMsg     "checksums defined in their scripts:"
    echo "" 1>&2
    for md5 in ${MISSINGMD5LIST[@]}; do
        echo "${md5:0:32}  ${md5:33}" 1>&2
    done
    echo "" 1>&2
  fi
  if [ "${MD5FAILEDLIST}" != "" ] || [ "${NALIST}" != "" ]; then
    echo "exit"
  fi
}
isArchive() {
  for ext in \\.zip \\.tar\\.bz2 \\.tar\\.gz \\.tar\\.xz \\.tgz \\.tar; do
    if [ "$(echo ${1} | grep ${ext}\$)" ]; then
      echo "true"
      return
    fi
  done
}
prepareSource() {
  local UNKNOWN_FORMAT="no"
  mkdir builddir
  for url in ${WGETLIST}; do
    ln -s ../src/$(basename ${url}) builddir
  done
  cd builddir
  PARENT_DIR="../"
  if [ "${2}" == "multi" ]; then
    mkdir subbuilddir
    cd subbuilddir
    PARENT_DIR="../../"
  fi
  mkdir "${1}-extracted"
  cd "${1}-extracted"
  if [ "$(echo ${1} | grep \\.zip\$)" ]; then
    displayMsg "... Extracting source code (ZIP)."
    unzip "${PARENT_DIR}${1}" 1> /dev/null
  elif [ "$(isArchive ${1})" ]; then
    displayMsg "... Extracting source code."
    tar xf "${PARENT_DIR}${1}"
  else
    cd ../
    rm -rf "${1}-extracted"
    if [ "$(echo ${1} | grep \\.img\$)" == "" ]; then
      displayMsg WARN "... Unknown source format. Not extracting '${1}'"
    fi
    local UNKNOWN_FORMAT="yes"
  fi
  if [ "${UNKNOWN_FORMAT}" != "yes" ]; then
    local  DIRCOUNT="$(ls -d */ 2> /dev/null | grep -c  '/' || echo -n '')"
    local FILECOUNT="$(ls -dp * 2> /dev/null | grep -cv '/' || echo -n '')"
    if [ "${DIRCOUNT}" == "1" ] && [ "${FILECOUNT}" == "0" ]; then
      mv "$(ls -d */)" ../
      cd ../
      rm -rf "${1}-extracted"
      cd "$(ls -d */)"
    fi
  fi
  if [ "$(basename ${SELECTEDPACKAGE})" == "kernel" ] ||
     [ $(echo "${TAGS}" | grep "\bkernel\b" || echo -n '') ]; then
    displayMsg "...... Preparing kernel source."
    make mrproper
    if [ -r "/sources/buildmgr/kernel.config" ]; then
      cp /sources/buildmgr/kernel.config .config
    else
      displayMsg WARN "...... No custom kernel configuration found."
      displayMsg WARN "...... Running 'make defconfig'."
      make defconfig &> /dev/null
    fi
    yes "" | make oldconfig &> /dev/null
  fi
}
BUILD() {
  local BUILDMGR_MAKEFLAGS="-j $(cat /proc/cpuinfo | grep processor | wc -l)"
  export MAKEFLAGS="${BUILDMGR_MAKEFLAGS}"
  if [ -r "/sources/buildmgr/scripts/extend.conf" ]; then
    . /sources/buildmgr/scripts/extend.conf
  fi
  WGETLIST=""
  MD5SUMLIST=""
  POSTINST=""
  TAGS=""
  PREINST_DONE=""
  if [ "$(echo ${1} | grep '/' 2> /dev/null || echo -n '')" == "" ]; then
    local SELECTEDPACKAGE="${BASESYSTEM}/${1}"
    local REPOFOLDER="${BASESYSTEM}"
  else
    local SELECTEDPACKAGE="${1}"
    local REPOFOLDER="$(echo ${1} | cut -d'/' -f1)"
  fi
  local RESUMELFS="$(cat /sources/buildmgr/resume)"
  if [ "${RESUMELFS}" == "1" ] && [ -e /buildmgr.root ]; then
    if [ -r "/sources/buildmgr/install-packages/${SELECTEDPACKAGE}.txz" ]; then
      displayMsg KEY "Selected '${SELECTEDPACKAGE}'..."
      displayMsg "... Precompiled package found."
      installPkgBuilt "/sources/buildmgr/packages/${SELECTEDPACKAGE}.txz"
      return
    fi
  fi
  cd "/sources"
  local SCRIPT_SRC="/sources/buildmgr/scripts/${SELECTEDPACKAGE}"
  . ${SCRIPT_SRC}
  if [ $(echo "${TAGS}" | grep "\bgroup\b" || echo -n '') ]; then
    return
  fi
  displayMsg KEY "Selected '${SELECTEDPACKAGE}'..."
  if [ "$(cat ${SCRIPT_SRC} | grep '\-j1')" != "" ] && 
     [ "${MAKEFLAGS}" == "${BUILDMGR_MAKEFLAGS}" ]; then
    displayMsg "... Disabling multi-core compilation for this package."
    unset MAKEFLAGS
  fi
  if [ ! -e "/buildmgr.root" ]; then
    prepareSource "$(basename $(echo ${WGETLIST} | cut -d' ' -f1))"
    displayMsg "... Building a temporary version of this software."
    local LOGNAME="$(basename ${SELECTEDPACKAGE}).prep"
    delayErrors
    preparation &> "/sources/buildmgr/logs/${LOGNAME}"
    handleErrorsNow "building the temporary package"
    cd "/sources"
    rm -rf builddir
  else
    PREINST_DONE="true"
    if [ $(echo "${TAGS}" | grep "\bpreinst\b" || echo -n '') ]; then
      displayMsg "... Running the pre-installation script."
      local LOGNAMEPREINST="$(basename ${SELECTEDPACKAGE}).preinst"
      delayErrors
      echo "Preinstallation of ${SELECTEDPACKAGE}..." 1>> \
            /sources/buildmgr/logs/install.log        2>> \
            /sources/buildmgr/logs/install.log
      preinst 1>> /sources/buildmgr/logs/install.log \
              2>> /sources/buildmgr/logs/install.log
      handleErrorsNow "running the pre-installation script for new software"
    fi
    if [ $(echo "${TAGS}" | grep "\bmulti\b") ]; then
      mkdir parts
      for urlx in ${WGETLIST}; do
        local SUBPACKAGE="$(basename $urlx)"
        if [ "$(isArchive ${SUBPACKAGE})" ]; then
          displayMsg ". Selected part '${SUBPACKAGE}'."
          
          prepareSource "${SUBPACKAGE}" multi
          local LOGNAMEI="$(basename ${SUBPACKAGE}).inst"
          buildPackage
          
          local PKGNAME="$(basename ${SELECTEDPACKAGE})"
          mv "/sources/buildmgr/newpackages/${REPOFOLDER}/${PKGNAME}.txz" \
             "/sources/parts/${SUBPACKAGE}.txz"
          cd /sources
          rm -rf builddir
        fi
      done
      displayMsg ". Combining packages in '${SELECTEDPACKAGE}'."
      cd parts
      for arch in $(ls -1 *.txz); do
        tar xf ${arch}
        cd "${PKGNAME}"
        tar xf pkgroot.tar --dereference
        cd ../
      done
      cd "${PKGNAME}"
      rm -rf MD5SUMS
      tar cf pkgroot.tar pkgroot --hard-dereference
      rm -rf pkgroot
      md5sum * > MD5SUMS
      cd ../
      tar --xz -cf /sources/buildmgr/newpackages/${REPOFOLDER}/${PKGNAME}.txz \
            ${PKGNAME}
      cd ../
      rm -rf parts
    else
      if [ "${WGETLIST}" != "" ]; then
        prepareSource "$(basename $(echo ${WGETLIST} | cut -d' ' -f1))"
      else
        mkdir builddir
        cd builddir
      fi
      local LOGNAMEI="$(basename ${SELECTEDPACKAGE}).inst"
      buildPackage
      cd "/sources"
      rm -rf builddir
    fi
    if [ "$(cat /sources/buildmgr/cleanenv)" == "1" ]; then
      touch /sources/buildmgr/cleanexit
      manualAbort
    fi
  fi
}
buildPackage() {
  local FAKEROOT="/sources/buildmgr/pkgroot"
  mkdir -p ${FAKEROOT}
  mkdir -p /etc/{profile.d,skel}
  displayMsg "... Preparing directory structure."
    unset DIRECTORIES i
    while IFS= read -r -d '' found; do
        DIRECTORIES[i++]="${found}"
    done < <(find / -path '/tools' -prune -o -path '/sources' -prune -o \
            -path '/dev' -prune -o -path '/proc' -prune -o -path '/sys' -prune \
            -o -type d -printf %G:%U\\0%m\\0%p\\0)
    unset SYMLINKS i
    while IFS= read -r -d '' found; do
        [ "${found}" == "/" ] && continue
        local ISDIRLINK=$(pushd "${found}" 2> /dev/null || echo -n '')
        if [ "${ISDIRLINK}" != "" ]; then
            SYMLINKS[i++]="${found}"
        fi
    done < <(find / -path '/tools' -prune -o -path '/sources' -prune -o \
            -path '/dev' -prune -o -path '/proc' -prune -o -path '/sys' -prune \
            -o -type l -print0)
    for (( i=0; i < ${#DIRECTORIES[@]}; )); do
        local OWNER="${DIRECTORIES[i++]}"
        local MODE="${DIRECTORIES[i++]}"
        local DIRNAME="${DIRECTORIES[i++]}"
        [ "${DIRNAME}" == "/" ] && continue
        mkdir --mode=${MODE} "${FAKEROOT}${DIRNAME}"
    done
    for found in "${SYMLINKS[@]}"; do
        local LINKTO=$(readlink "${found}")
        if [ "${LINKTO:0:1}" == "/" ]; then
            local LINKTO="${FAKEROOT}${LINKTO}"
        fi
        ln -sv "${LINKTO}" "${FAKEROOT}${found}"
    done
  if [ "$(basename ${SELECTEDPACKAGE})" == "buildiso" ]; then
    displayMsg "... Compressing the system and building an ISO image."
  else
    displayMsg "... Building the software."
  fi
  local LOGFILE="/sources/buildmgr/logs/${LOGNAMEI}"
  delayErrors
  installation &> ${LOGFILE}
  handleErrorsNow "building the software"
  /tools/bin/find ${FAKEROOT}/{,usr/}{bin,lib,sbin} -type f \
    -exec /tools/bin/strip --strip-debug '{}' ';' &> /dev/null || echo -n ''
  rm -rf {,${FAKEROOT}}/usr/share/info/dir
    for found in "${SYMLINKS[@]}"; do
        rm -rf "${FAKEROOT}${found}"
    done
    for (( i="${#DIRECTORIES[@]} - 1"; i > -1; )); do
        local DIRNAME="${DIRECTORIES[i--]}"
        local MODE="${DIRECTORIES[i--]}"
        local OWNER="${DIRECTORIES[i--]}"
        [ "${DIRNAME}" == "/" ] && continue
        cd "${FAKEROOT}${DIRNAME}" &> /dev/null &&
        if [ "$(ls -A)" == "" ]; then
            cd ../
            rmdir "${FAKEROOT}${DIRNAME}"
        fi
    done
    cd "${FAKEROOT}"
    while IFS= read -r -d '' found; do
        [ "${found}" == "." ] && continue
        local OWNER=$(stat --format=%u:%g "${found}")
        if [ "${OWNER}" != "0:0" ]; then
            local CDIR="${found:1}"
            displayMsg WARN "...... Directory not owned by root: '${CDIR}'"
        fi
    done < <(find . -type d -print0)
  cd "${FAKEROOT}"
  if [ "$(ls -A)" == "" ]; then
    local NOPACKAGE="1"
  fi
  cd ../
  local PKGNAME="$(basename ${SELECTEDPACKAGE})"
  if [ ! "${NOPACKAGE}" ]; then
    displayMsg "... Creating package archive."
    mkdir -p "temp.txz/${PKGNAME}"
    tar cf "temp.txz/${PKGNAME}/pkgroot.tar" pkgroot --hard-dereference
    cp "${SCRIPT_SRC}" "temp.txz/${PKGNAME}/buildscript"
    cd "temp.txz/${PKGNAME}"
    md5sum * > MD5SUMS
    cd ../
    tar --xz -cf "${PKGNAME}.txz" "${PKGNAME}"
    mkdir -p "../newpackages/${REPOFOLDER}"
    mv "${PKGNAME}.txz" "../newpackages/${REPOFOLDER}"
    cd "${FAKEROOT}/../"
    rm -rf ${FAKEROOT}
    rm -rf temp.txz
    installPkgBuilt "/sources/buildmgr/newpackages/${REPOFOLDER}/${PKGNAME}.txz"
  else
    displayMsg WARN "There were no files found to package."
  fi
}
installPkgBuilt() {
  local PKG="${1}"
  local TMPDIR="/tmp/install.$$.tmp" 
  if [ -e ${TMPDIR} ]; then
    displayMsg ERR "Temporary installation directory exists."
    queryAbort
  fi
  mkdir ${TMPDIR}
  cd ${TMPDIR}
  tar xf "${PKG}"
  cd "$(ls -d */)"
  md5sum -c MD5SUMS &> /dev/null; MD5ERR="$?"
  if [ "${MD5ERR}" != "0" ]; then
    displayMsg ERR "The contents of the package archive are corrupted."
    queryAbort
  fi
  . buildscript
  if [ $(echo "${TAGS}" | grep "\bpreinst\b" || echo -n '') ] &&
      [ "${PREINST_DONE}" == "" ]; then
      displayMsg "... Running the pre-installation script."
      delayErrors
      echo "Preinstallation of ${PKG}..."      1>> \
            /sources/buildmgr/logs/install.log 2>> \
            /sources/buildmgr/logs/install.log
      preinst 1>> /sources/buildmgr/logs/install.log \
              2>> /sources/buildmgr/logs/install.log
      handleErrorsNow "running the pre-installation script"
  fi
  displayMsg "... Installing to system."
  ln -s / pkgroot
  tar xf pkgroot.tar --dereference

  ldconfig &> /dev/null || echo -n ''
  local PFL="$(tar tf pkgroot.tar          | \
                 grep '^pkgroot/etc/profile' \
                    || echo -n '')"
  if [ "${PFL}" ]; then
      displayMsg "... Reloading system profile."
      . /etc/profile
  fi
  if [ "$(cat /sources/buildmgr/installdir)" != "" ]; then
      export LFSCRIPT_INSTALL="true"
  fi
  if [ "$(echo ${POSTINST} | grep '^now\b')" != "" ]; then
    if [ -r "/sources/buildmgr/scripts/install.conf" ]; then
      . /sources/buildmgr/scripts/install.conf
    fi
    displayMsg "... Running the post-installation script."
    delayErrors
    echo "Postinstallation of ${PKG}..."   1>> \
        /sources/buildmgr/logs/install.log 2>> \
        /sources/buildmgr/logs/install.log
    postinst | tee -a /sources/buildmgr/logs/install.log
    handleErrorsNow "running the post-installation script"
  elif [ "${POSTINST}" ]; then
    cp buildscript /sources/buildmgr/postinst/$(basename ${PKG}).sh
  fi
  cd /
  rm -rf ${TMPDIR}
}
findBasesystem() {
  local STMS="$(ls -1 ${SCRIPTSDIR}/*/buildbase.lfs 2> /dev/null || echo -n '')"
  local BASECOUNT="0"
  for basesystem in ${STMS}; do
    BASECOUNT="$(( ${BASECOUNT} + 1 ))"
    if [ "2" -le "${BASECOUNT}" ]; then
      displayMsg ERR "Multiple base systems available. Use -b <system>, not -B."
      echo "exit"
    else
      TMP_BASESYSTEM=$(echo "${STMS}" | sed \
                        "s@${SCRIPTSDIR}/@@;s@/buildbase.lfs@@")
    fi
  done
  if [ "${BASECOUNT}" == "0" ]; then
    displayMsg ERR "No base system scripts available."
    echo "exit"
  fi
}
verifyEnvironment() {
  if [ "${TMP_MEMTEST}" == "1" ]; then
    local MEMREQ_MB="950" 
    local MEMSIZE_KB="$(grep MemTotal /proc/meminfo | awk '{print $2}')"
    local MEMSIZE_MB="$((${MEMSIZE_KB} / 1024))"
    if [ "${MEMSIZE_MB}" -lt "${MEMREQ_MB}" ]; then
      displayMsg ERR "The system memory size (as reported by /proc/meminfo) is"
      displayMsg ERR "${MEMSIZE_MB} MiB. This is less than the recommended"
      displayMsg ERR "minimum of 1 GiB and *could* cause build failures in"
      displayMsg ERR "large software (for example in the temporary build of"
      displayMsg ERR "GCC) if it is significantly less."
      displayMsg ERR ""
      displayMsg ERR "Enough SWAP space may (or may not) solve these problems,"
      displayMsg ERR "but can slow down the build process."
      displayMsg ERR ""
      displayMsg ERR "If you want to build your system anyway; Use the -M"
      displayMsg ERR "switch on LFScript to tell it to ignore memory size."
      echo "exit"
    fi
  fi
  PREREQ_FAIL="0"
  echo 'main(){}' > dummy.c && gcc -o dummy dummy.c
  if [ ! -x dummy ]; then
    PREREQ_FAIL="1"
  fi
  rm -f dummy.c dummy
  if [ "$(basename $(readlink -f /bin/sh))" != "bash" ]; then
    PREREQ_FAIL="1"
  fi
  if [ "${PREREQ_FAIL}" != "0" ]; then
      displayMsg ERR "This system does not meet the LFS host system"
      displayMsg ERR "requirements. Please read the Linux From Scratch preface"
      displayMsg ERR "(section 'Host System Requirements') to make your system"
      displayMsg ERR "compatible."
      displayMsg ERR ""
      displayMsg ERR "Note that when you no longer receive this message, it"
      displayMsg ERR "does not necessarily mean that your system now does meet"
      displayMsg ERR "the requirements. LFScript only tests a couple of very"
      displayMsg ERR "basic aspects of your system."
      echo "exit"
  fi
}
if [ "$(echo ${PWD} | grep ' ' || echo -n '')" != "" ]; then
  displayMsg ERR "LFScript can't be run in a directory which contains spaces."
  echo "exit"
fi
  STARTDIR="${PWD}"
SCRIPTSDIR="${PWD}/scripts"
  PACKSDIR="${PWD}/packages-$(uname -m)"
SOURCESDIR="${PWD}/sources"
   LOGSDIR="${PWD}/logs"
if [ "${1}" == "--resumebasebuild" ]; then
  resumeBaseBuild
else
  TMP_UID="$(date +%Y-%m-%d_%H:%M:%S)"
  TMP_AUTOABORT="0"
  TMP_LISTONLY="0"
  TMP_SOURCESONLY="0"
  TMP_LISTMD5="0"
  TMP_RESUME="0"
  TMP_BACKUP_TC="1"
  TMP_INSTALL=""
  TMP_SECINSTALL=""
  TMP_CLEANENV="0"
  TMP_BASESYSTEM=""
  TMP_KERNELCONFIG=""
  TMP_MEMTEST="1"
  TMP_EXTRAPACKS=""
  TMP_DEPLEVELS="RECOMMENDS"
  TMP_RESUMEX=""
  TMP_SKIP=""
  displayMsg "lfscript - legal stuff and bla bla bla"
  while getopts 's:i:I:ALMSHBb:k:x:uU:Cr' OPTION 2> /dev/null; do
    case ${OPTION} in
      i)
         TMP_INSTALL="${OPTARG}"
         if [ ! -d "${TMP_INSTALL}" ]; then
           displayMsg ERR "Target directory does not exist (-i)."
           echo "exit"
         fi
         mountpoint ${TMP_INSTALL} &> /dev/null || {
           displayMsg ERR "Target directory is not a mountpoint (-i)."
           echo "exit"            
         }
         ;;
      I)
         TMP_SECINSTALL="${OPTARG}"
         if [ ! -d "${TMP_SECINSTALL}" ]; then
           displayMsg ERR "Target temporary directory does not exist (-I)."
           echo "exit"
         fi
         ;;
      A)
         TMP_AUTOABORT="1"
         ;;
      L)
         TMP_LISTONLY="1"
         ;;
      S)
         TMP_SOURCESONLY="1"
         ;;
      H)
         TMP_SOURCESONLY="1"
         TMP_LISTMD5="1"
         ;;
      u)
         TMP_BACKUP_TC="0"
         TMP_RESUME="1"
         ;;
      b)
         TMP_BASESYSTEM="${OPTARG}"
         if [ ! -r "${SCRIPTSDIR}/${TMP_BASESYSTEM}/buildbase.lfs" ]; then
           displayMsg ERR "Base system script not found or unreadable (-b)."
           echo "exit"
         fi
         ;;
      B)
         findBasesystem
         ;;
      k)
         TMP_KERNELCONFIG="${OPTARG}"
         if [ ! -r "${TMP_KERNELCONFIG}" ]; then
           displayMsg ERR "Kernel configuration not found or unreadable (-k)."
           echo "exit"
         fi
         ;;
      C)
         TMP_BACKUP_TC="0"
         TMP_RESUME="1"
         TMP_CLEANENV="1"
         ;;
      r)
         TMP_DEPLEVELS=""
         ;;
      s)
         TMP_SKIP="${OPTARG}"
         ;;
      x)
         TMP_EXTRAPACKS="${OPTARG}"
         ;;
      U)
         TMP_BACKUP_TC="0"
         TMP_RESUME="1"
         TMP_RESUMEX="${OPTARG}"
         ;;
      M)
         TMP_MEMTEST="0"
         ;;
      ?)
         displayMsg WARN "Unknown options set."
         displayMsg WARN "Read the manual for usage instructions."
         echo "exit"
         ;;
    esac
  done
  if [ "${TMP_CLEANENV}" == "1" ] && [ "${TMP_INSTALL}" != "" ]; then
    displayMsg ERR "The '-C' and '-i' switches are not compatible with each"
    displayMsg ERR "other."
    echo "exit"
  fi
  if [ "${TMP_CLEANENV}" == "1" ] && [ "${TMP_RESUMEX}" != "" ]; then
    displayMsg ERR "The '-C' and '-U' switches are not compatible with each"
    displayMsg ERR "other."
    echo "exit"
  fi
  if [ "${TMP_CLEANENV}" == "1" ] && [ "${TMP_SECINSTALL}" != "" ]; then
    displayMsg ERR "The '-C' and '-I' switches are not compatible with each"
    displayMsg ERR "other."
    echo "exit"
  fi
  if [ "${TMP_RESUME}" != "1" ] && [ "${TMP_SECINSTALL}" != "" ]; then
    displayMsg ERR "The '-I' switch requires that at least the toolchain has"
    displayMsg ERR "already been compiled. Add the '-u' switch."
    echo "exit"
  fi
  if [ "${TMP_BASESYSTEM}" != "" ]; then
    verifyEnvironment
    deployScripts
  else
    displayMsg ERR "You should at least specify a base system."
    displayMsg ERR "Read the manual for usage instructions."
  fi
fi
