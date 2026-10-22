#!/usr/bin/env bash
# 8-BALL CLUB launcher for Linux / macOS
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$DIR/dist/8-ball-club.jar"
if [ ! -f "$JAR" ]; then
  echo "dist/8-ball-club.jar not found. Build with: ./build.sh"
  exit 1
fi
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA="$(command -v java || true)"
fi
if [ -z "$JAVA" ]; then
  echo "Java 17+ not found. Install a JDK, e.g. https://adoptium.net"
  exit 1
fi
exec "$JAVA" -jar "$JAR" "$@"
