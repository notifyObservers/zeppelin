#!/usr/bin/env bash
#
#
HERE="`dirname \"$0\"`"             # relative
HERE="`( cd \"$HERE\" && pwd )`"    # absolutized and normalized
if [ -z "$HERE" ] ; then
    exit 1  # fail
fi
CI_DIR="$HERE"
MVN_CLEAN_COMPILE_OUT="/tmp/clean_compile.out"
# Deploy into this directory, to run license checks on all jars staged for deployment.
# This helps us ensure that ALL artifacts we deploy to maven central adhere to our license conditions.
MVN_VALIDATION_DIR="/tmp/flink-validation-deployment"
# source required ci scripts
source "${CI_DIR}/maven-utils.sh"
echo "Maven version:"
run_mvn -version
echo "=============================================================================="
echo "Compiling Flink on Zeppelin"
echo "=============================================================================="
EXIT_CODE=0
if [ -z "$JFROG_USERNAME_ENV" ]; then
  echo "ERROR: jfrog username isn't provided"
  exit 1
fi
if [ -z "$JFROG_PASSWORD_ENV" ]; then
  echo "ERROR: jfrog password isn't provided"
  exit 1
  echo
fi
if [ -z "$BUILD_VERSION" ]; then
  echo "ERROR: release version isn't provided"
  exit 1
  echo
fi
run_mvn clean
# override Maven coordinates to LinkedIn version
# This line will change .pom files automatically. If it runs in local, it's necessary to manually revert all the changes.
run_mvn versions:set -DnewVersion=$BUILD_VERSION -DoldVersion=* -DgroupId=com.microsoft.azure.hdinsight -DartifactId=flink-on-zeppelin
run_mvn deploy -Djfrog.exec.publishArtifacts=true -Djfrog.publisher.password=$JFROG_PASSWORD_ENV -Djfrog.publisher.username=$JFROG_USERNAME_ENV | tee $MVN_CLEAN_COMPILE_OUT
EXIT_CODE=${PIPESTATUS[0]}
if [ $EXIT_CODE != 0 ]; then
    echo "=============================================================================="
    echo "Compiling Flink Operator failed."
    echo "=============================================================================="
    grep "0 Unknown Licenses" target/rat.txt > /dev/null
    if [ $? != 0 ]; then
        echo "License header check failure detected. Printing first 50 lines for convenience:"
        head -n 50 target/rat.txt
    fi
    exit $EXIT_CODE
fi