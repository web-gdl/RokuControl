#!/usr/bin/env bash
# Compila, empaqueta y firma Roku Control sin Gradle: aapt2 -> javac -> D8 -> apksig.
# Requisitos: JDK 17+, tools/android-36.jar, tools/lib/jadx-1.5.1-all.jar (D8 y apksig),
# tools/ap/prebuilt/linux/aapt2_64. Ver docs/COMPILAR.md.
set -euo pipefail
cd "$(dirname "$0")"

VERSION_NAME="$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml | head -1)"
VERSION_CODE="$(sed -n 's/.*android:versionCode="\([^"]*\)".*/\1/p' AndroidManifest.xml | head -1)"
ANDROID_JAR=tools/android-36.jar
TOOLS_JAR=tools/lib/jadx-1.5.1-all.jar
AAPT2=tools/ap/prebuilt/linux/aapt2_64
KS=keystore/release.p12
KS_PASS="${KEYSTORE_PASSWORD:-rokuremote}"
OUT=out/build
APK="out/RokuControl-${VERSION_NAME}.apk"

rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/signer"

echo "== recursos"
"$AAPT2" compile --dir res -o "$OUT/res.zip"
"$AAPT2" link -I "$ANDROID_JAR" --manifest AndroidManifest.xml \
  --min-sdk-version 26 --target-sdk-version 36 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  -o "$OUT/base.apk" "$OUT/res.zip"

echo "== javac (Java 8, sin avisos permitidos)"
javac --release 8 -Xlint:all -Xlint:-options -classpath "$ANDROID_JAR" -d "$OUT/classes" $(find src -name '*.java')

echo "== D8"
java -cp "$TOOLS_JAR" com.android.tools.r8.D8 --release --min-api 26 --lib "$ANDROID_JAR" \
  --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

echo "== empaquetar"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -X ../unsigned.apk classes.dex)

if [ ! -f "$KS" ]; then
  echo "== creando clave de firma local (keystore/release.p12): CONSÉRVALA para poder actualizar la app"
  keytool -genkeypair -keystore "$KS" -storetype PKCS12 -alias release -keyalg RSA -keysize 3072 \
    -validity 10000 -storepass "$KS_PASS" -dname "CN=Roku Control" >/dev/null 2>&1
fi

echo "== firma v2+v3"
javac -classpath "$TOOLS_JAR" -d "$OUT/signer" tools-src/Sign.java
java -cp "$OUT/signer:$TOOLS_JAR" Sign "$KS" "$KS_PASS" release "$OUT/unsigned.apk" "$APK"
sha256sum "$APK"
