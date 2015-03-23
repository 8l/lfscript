#!/bin/bash
set -e

BINNAME="lfclass" \
JARNAME="libLFClass.jar" \
MAINCLASS="org.lfscript.ExecArbiter" \
  bash make/generic-build.sh $@
