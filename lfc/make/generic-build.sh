#!/bin/bash
# Version 2014-07-20

# Generic Compilation Script for Java projects (using Avian ProGuard and UPX)
# Copyright (c) 2011-2014 Marcel van den Boer
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
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.

# NOTICE:
#   Run this script without arguments to see usage instructions.

set -e

main() {
    local DEFAULT_JAVA_HOME="/usr/lib/jvm/java-6-openjdk
                             /usr/lib/jvm/java-7-openjdk-amd64
                             /usr/lib/jvm/java-7-openjdk-armhf"

    if [ "${JAVA_HOME}" == "" ]; then
        echo -n "Autodetecting JAVA_HOME... "

        for dir in ${DEFAULT_JAVA_HOME}; do
            if [ -d "${dir}" ]; then
                export JAVA_HOME="${dir}"
                echo "${JAVA_HOME}"
                break
            fi
        done

        if [ "${JAVA_HOME}" == "" ]; then
            echo "Failed."
            echo "Please set 'JAVA_HOME' manually."
            exit 1
        fi
    fi

    pushd java
    rebuild_$@
    popd
}

rebuild_clean() {
    rm -rf ../build
}

rebuild_pure() {
    rm -rf ../3rdparty
}

rebuild_purge() {
    rebuild_clean
    rebuild_pure
}

rebuild_jar() {
    if [ "${JARNAME}" == "" ]; then
        echo "ERROR: 'JARNAME' not set."
        exit 1
    fi

    rm -rf ../build/class
    mkdir -p ../build/class

    "${JAVA_HOME}/bin/javac" -d ../build/class $(find . -name *.java)

    pushd ../build/class

    "${JAVA_HOME}/bin/jar" cf ../${JARNAME} *

    popd

    rm -rf ../build/class
}

rebuild_api() {
    rm -rf ../build/api

    "${JAVA_HOME}/bin/javadoc" -d ../build/api -sourcepath . \
        -link http://download.oracle.com/javase/6/docs/api/ @options
}

rebuild_bin() {
    if [ "${BINNAME}" == "" ]; then
        echo "ERROR: 'BINNAME' not set."
        exit 1
    elif [ "${BINNAME}" == "vm" ]; then
        echo "ERROR: 'BINNAME' must not be the reserved word 'vm'."
        exit 1
    fi

    if [ "${MAINCLASS}" == "" ]; then
        echo "ERROR: 'MAINCLASS' not set."
        exit 1
    fi

    mkdir -p ../build
    mkdir -p ../3rdparty
    cd ../3rdparty

    # Set up architecture
    case $(uname -m) in
        i?86)
            local ARCH="i386"
            local UPX_ARCH="${ARCH}"
            local UPX_MD5="15fc83267ca9ac88d9c2dbc359e2ab8e"
            ;;
        x86_64)
            local ARCH="x86_64"
            local UPX_ARCH="amd64"
            local UPX_MD5="5e0cf0d10624e64f8538eab5563b71af"
            ;;
        arm*)
            local ARCH="arm"
            local UPX_ARCH="armeb"
            local UPX_MD5="447d96a1235f94a5e766ab988f56be81"
            ;;
        *)
            echo "Unknown architecture: $(uname -m)" >&2
            ;;
    esac

    # Fetch UPX
    local UPX_FOL="upx-3.91-${UPX_ARCH}_linux"
    local TGZ="${UPX_FOL}.tar.bz2"
    if [ ! -r ${TGZ} ]; then
        echo "Downloading UPX..."
        wget http://upx.sourceforge.net/download/${TGZ}
        if [ "$(md5sum ${TGZ})" != "${UPX_MD5}  ${TGZ}" ]; then
            echo "MD5 checksum failure"
            return 1
        fi
    fi
    rm -rf ../build/${UPX_FOL}
    cd ../build
    tar xf ../3rdparty/${TGZ}
    cd ../3rdparty

    # Fetch ProGuard
    local PRO_MD5="5feb242751faf361a0a45c2785f0d34d"
    local PRO_VER="4.11"
    local PRO_FOL="proguard${PRO_VER}"
    local TGZ="${PRO_FOL}.tar.gz"
    if [ ! -r ${TGZ} ]; then
        echo "Downloading ProGuard..."
        local BASE="http://downloads.sourceforge.net/project/proguard"
        wget ${BASE}/proguard/${PRO_VER}/${TGZ}
        if [ "$(md5sum ${TGZ})" != "${PRO_MD5}  ${TGZ}" ]; then
            echo "MD5 checksum failure"
            return 1
        fi
    fi
    rm -rf ../build/${PRO_FOL}
    cd ../build
    tar xf ../3rdparty/${TGZ}
    cd ../3rdparty

    # Fetch Avian
    local AVIAN_MD5="1ae35de2e1b5f0cba57b13f3556c85db"
    local AVIAN_FOL="avian"
    local TGZ="${AVIAN_FOL}-1.0.1.tar.bz2"
    if [ ! -r ${TGZ} ]; then
        echo "Downloading Avian..."
        wget http://oss.readytalk.com/avian-web/${TGZ}
        if [ "$(md5sum ${TGZ})" != "${AVIAN_MD5}  ${TGZ}" ]; then
            echo "MD5 checksum failure"
            return 1
        fi
    fi
    rm -rf ../build/${AVIAN_FOL}
    cd ../build
    tar xf ../3rdparty/${TGZ}
    cd ../3rdparty

    # Build Avian
    cd ../build/${AVIAN_FOL}
    make
    cp build/linux-${ARCH}/avian ../vm-${ARCH}
    mkdir embedded
    cd embedded
    ar x ../build/linux-${ARCH}/libavian.a
    mkdir stage1
    (cd stage1 && jar xf ../../build/linux-${ARCH}/classpath.jar)

    pushd ../../../java
    "${JAVA_HOME}/bin/javac" -d ../build/${AVIAN_FOL}/embedded/stage1 \
            -bootclasspath ../build/${AVIAN_FOL}/embedded/stage1 \
            "$(echo ${MAINCLASS} | sed 's@\.@/@g').java"
    popd

    # Run ProGuard
    echo "-keep class ${MAINCLASS} {"                     > keepmethods.pro
    echo "public static void main(java.lang.String[]);}" >> keepmethods.pro
    "${JAVA_HOME}/bin/java" -jar ../../${PRO_FOL}/lib/proguard.jar \
            -injars stage1 -outjars stage2 @../vm.pro \
            @keepmethods.pro

    cd stage2 # "stage1" if ProGuard is disabled for debugging
    "${JAVA_HOME}/bin/jar" cf ../boot.jar *
    cd ../

    ../build/linux-${ARCH}/binaryToObject/binaryToObject boot.jar boot-jar.o \
            _binary_boot_jar_start _binary_boot_jar_end linux ${ARCH}

    cp -v ../../../make/driver.cpp main.cpp
    sed -i "s/<start_class>/${MAINCLASS}/g" main.cpp

    g++ -I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux \
            -D_JNI_IMPLEMENTATION_ -c main.cpp -o main.o
    g++ -rdynamic *.o -ldl -lpthread -lz -o ../../${BINNAME}-${ARCH}

    cd ../../

    # Strip and compress with UPX
    ./${UPX_FOL}/upx --lzma --best ${BINNAME}-${ARCH}
    ./${UPX_FOL}/upx --lzma --best vm-${ARCH}

    rm -rf ${AVIAN_FOL} ${PRO_FOL} ${UPX_FOL}

    # Ensure executability
    chmod +x ${BINNAME}-${ARCH}
}

if [ "${1}" == "" ]; then
    echo ""
    echo "Usage: bash ${0} [option]"
    echo ""
    echo "Files:"
    echo "  Source code should be present in a folder called 'java'. The file"
    echo "  'java/options' should be present and should contain at least the"
    echo "  name of one package to document, if you want to generate API"
    echo "  documentation."
    echo "  A generic driver should be present as 'make/driver.cpp' if you"
    echo "  want to be able to create standalone executables."
    echo "  All output is placed in a folder called 'build'. Any downloaded"
    echo "  third party applications are placed in a folder called '3rdparty'."
    echo ""
    echo "Options:"
    echo "   jar      -  Build a jar file containing the entire class library"
    echo "   bin      -  Build a binary executable (using Avian's class lib)"
    echo "   api      -  Build API documentation"
    echo ""
    echo "   clean    -  Remove all built files"
    echo "   pure     -  Remove all downloaded 3rd party files"
    echo "   purge    -  Same as running both 'clean' and 'pure'"
    echo ""
    echo "Be sure to set the variable 'JARNAME', when using option 'jar', and"
    echo "the variables 'BINNAME' and 'MAINCLASS' when using option 'bin'."
    echo ""
    echo "Additionally, you may want to set the JAVA_HOME variable to the"
    echo "location of your JDK, otherwise a default value is used."
    echo ""
else
    main $@
fi

