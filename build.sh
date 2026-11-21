#!/usr/bin/env bash
# Builds Velthon Launcher:  ./build.sh out.apk
#   Java sources (src/) -> classes -> dex -> smali -> apktool project (app/) -> signed APK
# Settings (environment):
#   ANDROID_JAR  android.jar to compile against (API 28)           APKTOOL  path to apktool.jar
#   D8           d8 from Android build-tools (else DX_CP: dx classpath)
#   KEYSTORE, KEY_ALIAS, KEY_PASS   release key; without them a throw-away debug key signs the APK
set -e
cd "$(dirname "$0")"
OUT=$(realpath -m "${1:-velthon-launcher.apk}")
ANDROID_JAR=${ANDROID_JAR:-/home/claude/ap/android-28/android.jar}
APKTOOL=${APKTOOL:-/home/claude/build141/tools/apktool.jar}
B=build; rm -rf $B && mkdir -p $B/classes $B/dex
javac -nowarn -source 8 -target 8 -encoding UTF-8 -bootclasspath "$ANDROID_JAR" -d $B/classes src/com/velthon/launcher/*.java > $B/javac.log 2>&1 \
  || { grep -v "^Note\|JAVA_TOOL" $B/javac.log; exit 1; }
if [ -n "$D8" ]; then "$D8" --min-api 23 --lib "$ANDROID_JAR" --output $B/dex $(find $B/classes -name '*.class')
else java -cp "${DX_CP:-/home/claude/dxbuild}" com.android.dx.command.Main --dex --min-sdk-version=23 --output=$B/dex/classes.dex $B/classes; fi
mkdir -p $B/tools && javac -nowarn -cp "$APKTOOL" -d $B/tools tools/Dis.java 2>/dev/null
java -cp "$B/tools:$APKTOOL" Dis $B/dex/classes.dex $B/smali >/dev/null 2>&1
rm -rf app/smali && cp -r $B/smali app/smali
java -jar "$APKTOOL" b app -o $B/unsigned.apk 2>&1 | grep -v JAVA_TOOL | tail -1
rm -rf app/smali app/build
if [ -n "$KEYSTORE" ]; then
  cp $B/unsigned.apk $B/s1.apk
  jarsigner -keystore "$KEYSTORE" -storepass "$KEY_PASS" $B/s1.apk "${KEY_ALIAS:-velthon}" >/dev/null
  python3 tools/apksign.py align $B/s1.apk $B/s2.apk
  python3 tools/apksign.py sign $B/s2.apk "$OUT" "${KEY_P12:?KEY_P12 needed for v2 signing}" "$KEY_PASS"
else
  K=$B/debug.jks
  keytool -genkeypair -keystore $K -storepass android -keypass android -alias debug -keyalg RSA -validity 3650 -dname CN=debug >/dev/null 2>&1
  if command -v apksigner >/dev/null; then zipalign -f 4 $B/unsigned.apk $B/al.apk && apksigner sign --ks $K --ks-pass pass:android --out "$OUT" $B/al.apk
  else cp $B/unsigned.apk "$OUT"; jarsigner -keystore $K -storepass android "$OUT" debug >/dev/null; fi
fi
ls -la "$OUT"
