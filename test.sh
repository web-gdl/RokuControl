#!/usr/bin/env bash
# Pruebas funcionales en la JVM contra un Roku simulado (necesita el puerto local 8060 libre).
set -euo pipefail
cd "$(dirname "$0")"
rm -rf out/classes out/test; mkdir -p out/classes out/test
javac --release 8 -Xlint:all -Xlint:-options -classpath tools/android-36.jar -d out/classes $(find src -name '*.java')
javac -Xlint:-options -classpath out/classes -d out/test $(find test -name '*.java')
java -cp out/classes:out/test app.rokuremote.AllTests
