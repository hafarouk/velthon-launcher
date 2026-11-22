#!/usr/bin/env bash
# Phone-free checks: source checks + unit tests on the computer. Usage: test/unit.sh  (ANDROID_JAR as for build.sh)
set -e
cd "$(dirname "$0")/.."
ANDROID_JAR=${ANDROID_JAR:-/home/claude/ap/android-28/android.jar}
python3 test/unit/check_sources.py
rm -rf build/unit && mkdir -p build/unit
if ! javac -nowarn -encoding UTF-8 -cp "$ANDROID_JAR" -d build/unit $(find stubs -name "*.java") src/com/velthon/launcher/*.java test/unit/UnitTests.java > build/unit.log 2>&1; then
  grep -v "^Note\|JAVA_TOOL" build/unit.log; echo "unit tests: the code does not compile"; exit 1
fi
# the classes under test use only plain Java at run time; android.jar is only needed to compile the rest
java -cp "build/unit:$ANDROID_JAR" com.velthon.launcher.UnitTests
