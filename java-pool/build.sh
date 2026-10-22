#!/usr/bin/env bash
# Build 8-BALL CLUB on Linux / macOS
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAR="${JAVA_HOME:+$JAVA_HOME/bin/}jar"
if ! command -v "$JAVAC" >/dev/null 2>&1; then
  echo "javac not found (need JDK 17+). On Windows use build.ps1."
  exit 1
fi
rm -rf out/classes
mkdir -p out/classes dist
find src/main/java -name '*.java' > out/sources.txt
"$JAVAC" -encoding UTF-8 --release 17 -d out/classes @out/sources.txt
rm -rf out/jar
cp -r out/classes out/jar
rm -f out/jar/com/pool8/DebugMain*.class out/jar/com/pool8/BallSheet*.class \
      out/jar/com/pool8/UiSmokeTest*.class out/jar/com/pool8/NetSmokeTest*.class \
      out/jar/com/pool8/InputSmokeTest*.class
"$JAR" --create --file dist/8-ball-club.jar --main-class com.pool8.Main -C out/jar .
echo "Built dist/8-ball-club.jar"
